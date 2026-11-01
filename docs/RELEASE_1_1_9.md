# Kilometrina 1.1.9: costs during an active trip

Active trips now offer a cost manager without stopping location tracking. The same manager is available from completed trip cards. Each parking/toll payment has its own stable ID, exact integer-cent amount, optional note, creation time and optional receipt. Users can edit and delete individual payments; the card separates mileage from additional costs and shows the combined amount.

The amount is saved before opening the document picker or camera. Cancellation or an import failure leaves the saved payment intact. Existing file limits and bounded receipt imports remain in effect. A receipt can be attached later, opened, or replaced. Replacing a receipt retains the previous file as a general trip attachment; deleting a cost retains its receipt as a general trip attachment. Deleting a general attachment clears the corresponding expense link without removing its amount. Camera access uses the system camera and a narrowly scoped FileProvider.

The form stays bound to its original trip ID when the trip finishes while it is open. Save operations outlive a dismissed UI coroutine in the ViewModel, and a stable payment ID makes retries idempotent. Unsaved amounts/notes require confirmation before dismissal. Saved payments survive a process/database restart.

## Storage and compatibility

Room schema 7 adds the `expenses` table without rebuilding trips or existing receipt BLOBs. Migration 6→7 converts existing positive category totals into separate legacy entries; totals, trip IDs and all receipts are retained. Fresh manual trips also seed their original category totals as entries. Completed-trip editors display category totals read-only and direct users to the cost manager.

Expense writes and total recalculation run in one transaction and update only cost columns. Finalizing a trip preserves its latest costs and GPS distance, and saving a completed-trip editor preserves the current ledger totals. Concurrent changes cannot replace expenses with an older trip snapshot. Category total overflow rejects and rolls back the whole write.

Backup schema 7 includes the ledger and receipt links. Versions 1–6 are imported by creating legacy entries from their existing totals. Restore checks unique payment IDs, valid categories/cent values, same-trip receipt links, unique receipt ownership and exact agreement between entries and trip totals before replacing data. Database snapshots/restores are transactional.

Monthly PDF and the main trip CSV retain their existing layout, hidden tariff/version and category totals. ZIP packages additionally include `stroski-YYYY-MM.csv` with separate payments and the exact paths of their linked receipt files. The app obtains the trip/expense/receipt list for the ZIP in one database transaction. Private trips and other months remain excluded.

Version code 119 reuses the approved release signing identity introduced in 1.1.7. No signing key is stored in Git.

## Validation

Regression coverage includes concurrent GPS/payment writes, stale completion/editor saves, idempotent retries, overflow rollback, incorrect/deleted receipt targets, database reopening, backup/restore with an 8 MiB receipt, receipt deletion and ZIP link reconciliation. Compose coverage exercises multiple payments, editing/deletion, invalid input, failed saves, finishing a trip with an open form, and simulated camera/document results and cancellations. Migration coverage includes every schema 1–6→7 path. All Android tests use a disposable emulator; no physical phone is modified.

Final results on 1 November 2026: **53 unit tests and 18 Android tests passed**. Independent verification passed for **18 PDF/CSV cases and six ZIP cases**, including reconciliation of the detailed expense CSV with the main report totals and validity of receipt paths. All seven fresh/legacy database cases passed. Release lint reported **zero errors and 19 advisory warnings**. The minified APK and AAB passed signing verification with the approved identity. The signed 1.1.8 installed on the emulator was updated in place to signed 1.1.9; launch succeeded and Android reported version code 119.
