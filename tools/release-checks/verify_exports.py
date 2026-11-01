from pathlib import Path
import csv, io, json, re, zipfile,sys,logging
from decimal import Decimal, ROUND_HALF_UP
import pdfplumber

import argparse
parser=argparse.ArgumentParser(description="Verify the native ExportMatrixTest output against independently calculated expectations")
parser.add_argument('--artifacts',type=Path,required=True)
parser.add_argument('--report-directory',type=Path)
args=parser.parse_args()
ROOT=args.report_directory or args.artifacts.parent
ROOT.mkdir(parents=True,exist_ok=True)
sys.stdout.reconfigure(encoding='utf-8')
logging.basicConfig(filename=ROOT/'pdf-parser-warnings.log',level=logging.WARNING)
OUT=args.artifacts
def cents(value): return Decimal(str(value)).quantize(Decimal('.01'),rounding=ROUND_HALF_UP)
def sl(value, decimals=2): return f'{value:.{decimals}f}'.replace('.',',')
def expected_total(row): return cents(Decimal(str(row['km']))*Decimal(str(row['rate'])))+Decimal(str(row['parking']))+Decimal(str(row['tolls']))
results=[]
for spec in json.loads((OUT/'export-manifest.json').read_text(encoding='utf-8')):
    name=spec['name'];rows=spec['rows']; pdfchecks={};csvchecks={}
    pdfpath=OUT/(name+'.pdf')
    pdfchecks['generated_nonempty']=pdfpath.stat().st_size>100
    with pdfplumber.open(pdfpath) as doc:
        texts=[p.extract_text() or '' for p in doc.pages]; text='\n'.join(texts)
        expected_pages={'trips_1':1,'trips_4':1,'trips_8':1,'trips_10':2,'trips_15':2,'trips_30':3,'multiple_vehicles':1,'many_vehicles':3,'mixed_private':1}.get(name,1)
        pdfchecks['page_count']=len(doc.pages)==expected_pages
        pdfchecks['a4_size']=all(abs(p.width-595)<.01 and abs(p.height-842)<.01 for p in doc.pages)
        bad_bounds=[]
        for n,p in enumerate(doc.pages,1):
            for c in p.chars+p.lines+p.rects:
                if c.get('x0',0)<-.1 or c.get('x1',0)>p.width+.1 or c.get('top',0)<-.1 or c.get('bottom',0)>p.height+.1: bad_bounds.append([n,c])
        pdfchecks['all_drawing_in_page_bounds']=not bad_bounds
        pdfchecks['summary_once_at_end']=sum(t.count('Povzetek') for t in texts)==1 and 'Povzetek' in texts[-1]
        pdfchecks['page_numbers']=all(f'Stran {i}' in t and t.rstrip().endswith(str(i)) for i,t in enumerate(texts,1))
        pdfchecks['no_obsolete_rate']=not re.search(r'€/km|EUR/km|0,43\s*€\s*/\s*km',text)
        pdfchecks['no_invalid_tokens']=not re.search(r'\b(?:NaN|null|Infinity|undefined)\b',text)
        pdfchecks['slovenian_characters']='Črt Šušterič' in text and 'Žična oprema' in text
        pdfchecks['vehicle_snapshots']=all((r['vehicle'] in text and r['plate'] in text) for r in rows if r['vehicle'])
        pdfchecks['parking_toll_labels']=all((r['parking']<=0 or ('Parkirnina '+sl(r['parking'])+' €') in text) and (r['tolls']<=0 or ('Cestnina '+sl(r['tolls'])+' €') in text) for r in rows)
        # Check the summary labels/amounts spatially, using the final page text.
        last=doc.pages[-1];summary_word=next(w for w in last.extract_words() if w['text']=='Povzetek')
        summarytext=last.crop((0,summary_word['top'],last.width,807)).extract_text() or ''
        mileage=sum((cents(Decimal(str(r['km']))*Decimal(str(r['rate']))) for r in rows),Decimal(0))
        parking=sum((Decimal(str(r['parking'])) for r in rows),Decimal(0));tolls=sum((Decimal(str(r['tolls'])) for r in rows),Decimal(0))
        vals=[str(len(rows)),sl(sum(r['km'] for r in rows),1)+' km',sl(cents(mileage))+' €',sl(cents(parking))+' €',sl(cents(tolls))+' €',sl(cents(mileage+parking+tolls))+' €']
        pdfchecks['summary_totals_match_source']=all(v in summarytext for v in vals)
        # Rows should end above summary and before footer separator, with no text collisions.
        overlaps=[]
        for n,p in enumerate(doc.pages,1):
            words=p.extract_words();head_end=148
            footer_top=min((w['top'] for w in words if w['text']=='Kilometrina' and w['top']>800),default=822)
            summary_top=min((w['top'] for w in words if w['text']=='Povzetek'),default=807)
            body=[w for w in words if w['top']>=head_end and w['top']<summary_top]
            if any(w['bottom']>summary_top or w['bottom']>=807 or w['bottom']>=footer_top for w in body):overlaps.append(n)
        pdfchecks['rows_clear_summary_footer']=not overlaps
        pdfchecks['row_totals_reconcile']=sum((cents(expected_total(r)) for r in rows),Decimal(0))==cents(mileage+parking+tolls)
        if name=='overflow_costs':pdfchecks['row_totals_match_source']=sl(cents(expected_total(rows[0])))+' €' in (doc.pages[0].crop((461,148,595,206)).extract_text() or '')
        (OUT/(name+'.pdf.txt')).write_text(text,encoding='utf-8')
    raw=(OUT/(name+'.csv')).read_text(encoding='utf-8-sig');table=list(csv.reader(io.StringIO(raw),delimiter=';'))
    header_idx=next(i for i,r in enumerate(table) if r and r[0]=='Datum'); actual=[]
    for r in table[header_idx+1:]:
        if not r:break
        actual.append(r)
    metadata={r[0]:r[1] for r in table if len(r)==2}
    csvchecks['row_count']=len(actual)==len(rows)
    csvchecks['ordering_dates_routes_vehicles']=all(a[0]==r['date'] and a[6]==r['route'] and a[9]==r['vehicle'] and a[10]==r['plate'] for a,r in zip(actual,rows))
    csvchecks['full_purpose_description_utf8']=all(a[7]==r['purpose'] and a[8]==r['description'] for a,r in zip(actual,rows)) and 'Črt Šušterič' in raw
    csvchecks['numeric_values']=all(a[11]==sl(r['km']) and a[12]==sl(cents(Decimal(str(r['km']))*Decimal(str(r['rate'])))) and a[13]==sl(r['parking']) and a[14]==sl(r['tolls']) and a[15]==sl(cents(expected_total(r))) for a,r in zip(actual,rows))
    csvchecks['summary_source_totals']=metadata['SKUPAJ EUR']==sl(cents(mileage+parking+tolls))
    csvchecks['printed_rows_reconcile']=sum((Decimal(a[15].replace(',','.')) for a in actual),Decimal(0))==Decimal(metadata['SKUPAJ EUR'].replace(',','.'))
    csvchecks['no_rate_or_invalid_tokens']=not re.search(r'€/km|EUR/km|\b(?:NaN|null|Infinity)\b',raw)
    results.append({'name':name,'pdf':'PASS' if all(pdfchecks.values()) else 'FAIL','csv':'PASS' if all(csvchecks.values()) else 'FAIL','pages':len(texts),'pdf_checks':pdfchecks,'csv_checks':csvchecks,'bounds_errors':bad_bounds})
packages=[]
for spec in json.loads((OUT/'package-results.json').read_text(encoding='utf-8')):
    checks={};name=spec['name'];checks['generated']=spec['generated']
    try:
        with zipfile.ZipFile(OUT/f'package_{name}.zip') as z:
            names=z.namelist();checks['zip_opens_crc']=z.testzip() is None
            checks['pdf_csv_present']=all(f'kilometrina-2026-10.{e}' in names for e in ('pdf','csv'))
            checks['unique_names']=len(names)==len(set(names))
            checks['valid_names']=all(not re.search(r'[\\:*?"<>|\x00-\x1f]',p) and not any(x in ('..','.') for x in p.split('/')) for p in names)
            expected_attach={'empty':0,'multiple':3,'duplicate_name':2,'sanitized_collision':2,'long_collision':2,'invalid_filename':1}[name]
            checks['expected_attachments']=sum(p.startswith('priloge/') for p in names)==expected_attach
            ledger=list(csv.reader(io.StringIO(z.read('stroski-2026-10.csv').decode('utf-8-sig')),delimiter=';'))[1:]
            checks['expense_ids_unique']=len({r[2] for r in ledger})==len(ledger)
            checks['expense_receipt_paths_valid']=all(not r[6] or r[6] in names for r in ledger)
            trip_csv=list(csv.reader(io.StringIO(z.read('kilometrina-2026-10.csv').decode('utf-8-sig')),delimiter=';'))
            totals={r[0]:r[1] for r in trip_csv if len(r)==2}
            checks['expense_totals_reconcile']=all(sum((Decimal(r[4].replace(',','.')) for r in ledger if r[3]==kind),Decimal(0))==Decimal(totals[key].replace(',','.')) for kind,key in [('Parkirnina','Parkirnine skupaj EUR'),('Cestnina','Cestnine skupaj EUR')])
            raw=z.read('kilometrina-2026-10.csv').decode('utf-8-sig');checks['private_other_month_excluded']='private.pdf' not in names and 'november.pdf' not in names and 'Vožnja 5' not in raw and 'Vožnja 6' not in raw
            checks['slovenian_utf8']='Črt Šušterič' in raw
            with pdfplumber.open(io.BytesIO(z.read('kilometrina-2026-10.pdf'))) as p:checks['pdf_parses']=len(p.pages)==1
    except Exception as e:checks['error']=str(e)
    packages.append({'name':name,'result':'PASS' if all(v is True for v in checks.values()) else 'FAIL','checks':checks,'generation_error':spec.get('error')})
report={'exports':results,'packages':packages}
(ROOT/'export-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
for r in results:print(r['name'],r['pdf'],r['csv'],'pages',r['pages'],'failed',[k for k,v in {**r['pdf_checks'],**r['csv_checks']}.items() if not v])
for r in packages:print('ZIP',r['name'],r['result'],r.get('generation_error'),r['checks'])

if any(r["pdf"]!="PASS" or r["csv"]!="PASS" for r in results) or any(p["result"]!="PASS" for p in packages):
    raise SystemExit(1)
