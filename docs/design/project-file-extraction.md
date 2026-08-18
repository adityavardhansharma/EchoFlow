# Project files → text the model can actually read

**Status:** Design — **Tier 1 native library built, verified working, and committed** (`app/src/main/jniLibs/arm64-v8a/libanydoc_kotlin.so`); the Kotlin/Room/UI work is not yet implemented.
**Owner:** —
**Last updated:** 2026-08-18
**Prerequisite reading:** none — this document is written so an engineer new to Kotlin, Android, JNI or Rust can build the whole thing top to bottom.

---

## 0. TL;DR (read this first)

Today, when you attach a file to a Project, only **plain-text formats** (`.txt`, `.md`, code, `.csv`…) are read and handed to the model. A **PDF, Word, Excel or image contributes nothing** — it is stored and listed, but the model never sees its content. That is the bug we are fixing.

We fix it with a **three-tier extraction pipeline** that runs **once per file, on the device**, and stores the result so every later chat message is cheap:

1. **Tier 1 — anydoc (local, free):** the bundled Rust library **already in the repo and proven working on-device** (§9) converts Word / Excel / PowerPoint / ODF / EPUB / CSV / **digital PDF** into clean Markdown. No API cost, no network, keeps tables and headings.
2. **Tier 2 — ML Kit OCR (local, free):** for **scanned PDFs and image files**, we render pages to bitmaps and run Google's on-device text recognition. No API cost, no network.
3. **Tier 3 — provider-direct (fallback):** if tiers 1–2 produce nothing usable *and* the chat's model can accept files/images, we send the raw file/image to the provider **on that message** using the attachment path the app already has. This is the only tier that costs tokens or leaves the device, and it only happens for files we genuinely could not read locally.

The extracted Markdown/text is **stored on the `project_documents` row** (reusing the existing `extractedText` column) and injected into the system prompt by the existing `ProjectManager.buildSystemContext()` — so **after the one-time extraction, nothing changes in the hot path** and cost per message stays at zero.

We also add an **Open-Source Licenses page under Echo Labs** to satisfy anydoc's MIT license (and the MIT/Apache licenses of every Rust crate compiled into the `.so`).

---

## 1. Glossary (skip if you know these)

| Term | Plain meaning |
|---|---|
| **`.so` file** | A compiled native library (like a `.dll` on Windows). Android loads it at runtime with `System.loadLibrary`. |
| **ABI** | The CPU flavour a `.so` is compiled for. Phones are almost all `arm64-v8a`; old phones `armeabi-v7a`; emulators `x86_64`. You ship one `.so` per ABI. |
| **NDK** | Android's toolchain for building native (`C`/`C++`/`Rust`) code. |
| **JNI** | The bridge that lets Kotlin/Java call functions inside a `.so`. |
| **UniFFI** | A Mozilla tool that **generates** the JNI + Kotlin glue for you from a Rust library, so you don't hand-write JNI. We use it. |
| **`jniLibs/`** | The folder in an Android module where prebuilt `.so` files live. Gradle bundles anything here into the APK automatically. |
| **Room** | The app's SQLite database layer. Tables are Kotlin `@Entity` classes; schema changes need a **migration**. |
| **Migration** | A small piece of SQL that upgrades an existing user's database from version N to N+1 **without deleting their data**. |
| **OCR** | "Optical Character Recognition" — reading text out of an *image* (a photo or a scanned page). |
| **Parsing** | Reading text out of a *structured file* (a `.docx` is really zipped XML). Parsing ≠ OCR. anydoc parses; ML Kit does OCR. |
| **Content parts** | The provider API lets one chat message carry an array of pieces: `{type:text}`, `{type:image_url}`, `{type:file}`. The app already builds these. |

---

## 2. Where we are today (the exact current behaviour)

**Import** (`ProjectManager.addDocument`, `app/src/main/java/com/echoflow/data/ProjectManager.kt`):
1. Copies the picked file into `filesDir/project_documents/<projectId>/<docId>`.
2. Calls `extractText(file, mime, name)` which **only** reads text-shaped formats (`TEXTUAL_MIME_TYPES` / `TEXTUAL_EXTENSIONS`). For a PDF it returns `null`.
3. Inserts a `ProjectDocument` row with `extractedText = null`.

**Injection** (`ProjectManager.buildSystemContext`, same file):
```kotlin
val docs = projectDocumentDao.getForProjectSync(projectId).filter { it.hasText }   // hasText = extractedText not blank
```
PDFs have `hasText == false`, so they are filtered out and never reach the model.

**The prompt is assembled** in `ChatViewModel` (`app/src/main/java/com/echoflow/ui/ChatViewModel.kt`, ~L1501):
```kotlin
val activeProjectId = _currentChatThreadId.value?.let { chatRepository.thread(it)?.projectId }
    ?: pendingProjectIfStillExists()
val systemPrompt = baseSystemPrompt + (activeProjectId?.let { projectManager.buildSystemContext(it) } ?: "")
```

**Conclusion:** the plumbing is correct; the only missing piece is *producing `extractedText` for non-plaintext files*. Everything in this document exists to fill that `extractedText` (now: Markdown) for more formats, plus a provider fallback for the few we still can't read locally.

---

## 3. Goals / non-goals

**Goals**
- Model can use PDF, Word, Excel, PowerPoint, ODF, EPUB, CSV, and images attached to a project.
- Extraction is **local and free** wherever possible; provider cost only as a last resort.
- Extraction happens **once** per file; per-message cost stays at zero (store-once).
- Works for **on-device/local models too** (they can't take files, so local extraction is the only thing that ever helps them).
- Existing users' databases upgrade cleanly; existing text files keep working.
- License obligations are satisfied in-app.

**Non-goals (for v1)**
- Re-extracting/OCR'ing on a schedule or watching for file edits (files are immutable copies).
- Perfect table fidelity from scanned pages (OCR loses structure — acceptable).
- Streaming partial extraction. Extraction is a short background job.

---

## 4. High-level architecture

```
          ┌──────────────────────── on file import (once) ────────────────────────┐
          │                                                                        │
  user picks file                                                                  │
          │                                                                        ▼
   copy into app storage ─────────►  FileExtractor.extract(file, mime, name)  ─────────────┐
          │                                    │                                            │
          │                          ┌─────────┴──────────┐                                 │
          │                          ▼                    ▼                                 │
          │                   Tier 1: anydoc         (image or scanned?)                    │
          │                   parse → Markdown             │                                │
          │                          │                     ▼                                │
          │              got usable text?           Tier 2: ML Kit OCR                      │
          │                     │        │            render pages → OCR                    │
          │                    yes       no                 │                               │
          │                     │        └───────►  got usable text? ── yes ──┐             │
          │                     ▼                          │                  │             │
          │            status = EXTRACTED                  no                 │             │
          │            extractedText = md                  ▼                  ▼             │
          │                     │              status = NEEDS_PROVIDER   status = EXTRACTED  │
          │                     └───────────────────────┬──┴──────────────────┘             │
          │                                             ▼                                    │
          │                       write extractedText + status onto project_documents row ──┘
          │
          └──────────────────────── on every chat message (hot path, unchanged cost) ───────┐
                                                                                             │
   buildSystemContext(projectId):                                                           │
     • append project instructions                                                          │
     • append each doc's extractedText where status == EXTRACTED                             ▼
     • for docs where status == NEEDS_PROVIDER AND model supports files:  ──► Tier 3
         attach the raw file/image to THIS message via localAttachment* (existing path)
```

**Design principle:** tiers 1–2 write durable text once. Tier 3 is the only per-message, per-cost path, and it fires only for files that are still `NEEDS_PROVIDER` **and** only when the current model can actually accept them.

---

## 5. The three tiers in detail

### 5.1 Tier 1 — anydoc (the workhorse)

- **What:** `github.com/firecrawl/anydoc`, Rust, MIT. Input: Word/PowerPoint/Excel/ODF/RTF/EPUB/CSV/**digital PDF**. Output: GitHub-flavoured **Markdown**. "Pure Rust, no ML models, no external services." ~<5 ms/doc.
- **Why Markdown:** LLMs read Markdown best — tables, headings and lists survive, which is exactly what a spreadsheet or a report needs.
- **Limit:** no OCR. A **scanned** (image-only) PDF has no embedded text, so anydoc returns little/nothing → we fall through to Tier 2.
- **How we call it:** through a small native `.so` we build once (see §8). From Kotlin it's one function: `Anydoc.toMarkdown(bytes, filename): String?`.

### 5.2 Tier 2 — ML Kit on-device OCR (images & scanned pages)

- **What:** Google ML Kit **Text Recognition v2**, on-device, free, private, API 21+. Recognises Latin/Chinese/Devanagari/Japanese/Korean.
- **Important correction:** ML Kit OCR works on **images only**. It does **not** open PDFs or DOCX. So:
  - **Image file** (`image/*`): feed the bitmap straight in.
  - **Scanned PDF:** use Android's built-in `PdfRenderer` to render each page to a `Bitmap`, then OCR each page and concatenate. Cap pages (e.g. first 30) to bound time/memory.
  - **DOCX/XLSX:** never OCR these — they are structured files, Tier 1 handles them. If Tier 1 failed on them, they go to Tier 3, not OCR.
- **Packaging = unbundled (decided, §16.1):** `com.google.android.gms:play-services-mlkit-text-recognition` → **~0 MB APK**, model pulled via Google Play Services on first use. Two consequences the code must handle: the first OCR needs a one-time model download (show "preparing OCR…"), and on **de-Googled devices Tier 2 is unavailable** — those files fall through to Tier 3 / the honest "can't read locally" state. Never crash on "model unavailable"; treat it as a normal miss.

### 5.3 Tier 3 — provider-direct (last resort, per-message)

- **What already exists:** `ChatMessage` (`app/src/main/java/com/echoflow/data/Models.kt`) carries `localAttachmentUri`, `localAttachmentMimeType`, `localAttachmentName`, and `OpenRouterPayloads.kt` already turns those into provider content parts:
  ```kotlin
  // PDF
  mapOf("type" to "file", "file" to mapOf(
      "filename" to name, "file_data" to "data:application/pdf;base64,$encoded"))
  // image
  mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:$mime;base64,$encoded"))
  ```
- **What we add:** when assembling a message in a project chat, for each `NEEDS_PROVIDER` document, attach it to the outgoing message the same way, **if and only if** the active model advertises file/vision capability.
- **Cost & privacy:** this is the only tier that spends tokens and sends bytes off-device. It runs **per message** (the provider is stateless), so it's genuinely a fallback. On-device/local models simply skip it (they can't take files), which is why Tiers 1–2 matter.
- **Capability gate:** we must not attach a PDF to a text-only model — it will error. Reuse/extend whatever capability signal the app has for vision (search `OpenRouterModelInfo` for `modalities`/vision flags). If unknown, be conservative: don't attach, and surface "this model can't read files" in the UI.

> **Reject the "let the model transcribe and cache it" idea.** LLMs don't transcribe long documents faithfully (they summarise, skip, hit output-token caps, hallucinate). Caching that would silently degrade quality. Our cache is always produced by a **deterministic** extractor (Tier 1/2), never by the model.

---

## 6. When extraction runs (lifecycle)

- **Primary:** at **import time**, right after the file is copied, as a background coroutine on `Dispatchers.IO`. The Files UI shows a per-row status while it runs.
- **Backfill (lazy, decided §16.1):** existing PDF/doc rows upgrade with `extractionStatus = NULL` (legacy). When a project is **next opened/used**, re-run extraction for its rows whose status is `NULL` and whose format is now supported. No upgrade-time sweep — gentler on CPU/battery, and behaviour is identical to today until the user opens that project (so nothing regresses).
- **Never on the chat hot path.** The only per-message work is Tier 3, and only for `NEEDS_PROVIDER` rows.

---

## 7. Data model & storage changes

We **reuse** `extractedText` (now holds Markdown or OCR text) and **add two nullable columns** to `project_documents`.

### 7.1 Entity — `app/src/main/java/com/echoflow/data/ProjectDocument.kt`

```kotlin
@Entity(
    tableName = "project_documents",
    foreignKeys = [ForeignKey(
        entity = Project::class, parentColumns = ["id"], childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")],
)
data class ProjectDocument(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val filePath: String,
    val extractedText: String? = null,      // existing — now Markdown/OCR text
    val addedAt: Long,
    // NEW — both nullable so the migration needs no SQL DEFAULT (see §9)
    val extractionStatus: String? = null,   // null = legacy/not-yet-processed
    val extractionTier: String? = null,     // which tier produced the text (for UI/debug)
) {
    val hasText: Boolean get() = !extractedText.isNullOrBlank()

    val status: ExtractionStatus
        get() = ExtractionStatus.from(extractionStatus)
}

enum class ExtractionStatus {
    PENDING,        // queued / running
    EXTRACTED,      // extractedText is usable (Tier 1 or 2)
    NEEDS_PROVIDER, // local extraction failed; try Tier 3 per message if model allows
    FAILED,         // unsupported and provider can't help either
    UNKNOWN;        // null in DB (legacy row) → treat as "re-extract"

    companion object {
        fun from(s: String?) = entries.firstOrNull { it.name == s } ?: UNKNOWN
    }
}

enum class ExtractionTier { ANYDOC, OCR, PROVIDER, LEGACY_TEXT }
```

**Why nullable, not defaulted:** the existing project migration (see `MIGRATION_21_22`) documents that entity fields carry no `@ColumnInfo(defaultValue)`, so a fresh Room install has no SQL defaults and the migrated table must match. `ALTER TABLE ADD COLUMN` **cannot add a `NOT NULL` column without a default**, so new columns are **nullable** — which also matches "null = legacy row, re-extract."

### 7.2 What goes in `extractedText`

- Tier 1: the Markdown string from anydoc.
- Tier 2: the OCR plain text (page-separated).
- Tier 3: **nothing stored** — Tier 3 attaches the raw file per message; `extractedText` stays null and `status = NEEDS_PROVIDER`.

The existing per-document budget cap in `buildSystemContext` (`MAX_DOC_CONTEXT_CHARS = 24_000`) still applies and still appends the "document truncated" note.

---

## 8. Database migration (v22 → v23) — keep the DB constant

**Rule:** additive, nullable columns only. **No data movement. No table rebuild. Existing rows untouched.**

### 8.1 Steps (all in `app/src/main/java/com/echoflow/data/AppDatabase.kt`)

1. **Bump the version** and update the comment:
   ```kotlin
   @Database(
       entities = [ /* …unchanged… */ Project::class, ProjectDocument::class ],
       version = 23, // v23: project_documents.extractionStatus + extractionTier
       exportSchema = true
   )
   ```
2. **Add the migration object** next to `MIGRATION_21_22`:
   ```kotlin
   internal val MIGRATION_22_23 = object : Migration(22, 23) {
       override fun migrate(db: SupportSQLiteDatabase) {
           // Additive, nullable — no DEFAULT (matches the no-defaultValue entity policy).
           db.execSQL("ALTER TABLE project_documents ADD COLUMN extractionStatus TEXT")
           db.execSQL("ALTER TABLE project_documents ADD COLUMN extractionTier TEXT")
           // Existing rows keep extractionStatus = NULL → read as UNKNOWN → backfilled later.
       }
   }
   ```
3. **Register it** in `getDatabase(...)`'s `.addMigrations(...)` list, after `MIGRATION_21_22,`:
   ```kotlin
   MIGRATION_21_22,
   MIGRATION_22_23,
   ```
4. **Export the new schema JSON.** `exportSchema = true` means a build writes `app/schemas/com.echoflow.data.AppDatabase/23.json`. Commit it — the tests read it.

### 8.2 Why existing data is safe

- No existing column changes type or nullability.
- New columns default to SQL `NULL`; the app reads `NULL` as `ExtractionStatus.UNKNOWN`.
- A `NULL`/`UNKNOWN` PDF row still injects nothing today (its `extractedText` is still null) — **identical behaviour to before the update** — until the backfill (§6) fills it. So the upgrade can never *regress* a working chat.

### 8.3 Tests to update (they already exist)

- `app/src/test/java/com/echoflow/data/ProjectsMigrationTest.kt` — add a case: open at v22 with a `project_documents` row, run `MIGRATION_22_23`, assert the two columns now exist and the old row's data is intact and its new columns are `NULL`.
- `app/src/test/java/com/echoflow/DatabaseUpgradeTest.kt` — the end-to-end "upgrade from v1 to latest" test picks up the new version automatically once the migration is registered; run it.

---

## 9. anydoc — the native module (BUILT AND VERIFIED)

> **Status change (2026-08-18): the biggest risk in this plan is retired.** The `.so` is built,
> committed, and has been proven working in a separate Android app. Everything in this section is now
> *description of a thing that exists*, not a proposal. §17 step 0 (the spike) is **done**.

### 9.1 What is in the repo right now

```
app/src/main/jniLibs/arm64-v8a/libanydoc_kotlin.so   7.0 MB
```

Verified by inspecting the binary:

- **Binding style:** UniFFI **0.29.5** scaffolding (`uniffi_core-0.29.5` in the symbol table), Rust
  namespace **`anydoc`**, crate/library name **`anydoc_kotlin`**.
- **16 KB page alignment:** every `LOAD` segment has `align 0x4000` → **Android 15+ safe**, no rebuild
  needed for the 16 KB requirement.
- **ABI:** `arm64-v8a` only. See §9.6.

### 9.2 The exported API (read off the binary, not guessed)

Six free functions are exported:

| Rust symbol | Kotlin (generated) | Purpose |
|---|---|---|
| `uniffi_anydoc_kotlin_fn_func_to_markdown` | `toMarkdown(path, …)` | file path → Markdown |
| `uniffi_anydoc_kotlin_fn_func_to_markdown_bytes` | `toMarkdownBytes(bytes, …)` | **bytes → Markdown ← the one we use** |
| `uniffi_anydoc_kotlin_fn_func_to_document` | `toDocument(…)` | structured `Document` model (blocks/tables/notes/assets) |
| `uniffi_anydoc_kotlin_fn_func_format_from_bytes` | `formatFromBytes(bytes)` | sniff format from content |
| `uniffi_anydoc_kotlin_fn_func_format_from_extension` | `formatFromExtension(ext)` | format from `.docx` etc. |
| `uniffi_anydoc_kotlin_fn_func_format_from_path` | `formatFromPath(path)` | format from a path |

Types it also exports (from the UniFFI metadata symbols): records `Document`, `Table`, `Cell`,
`ListItem`, `Note`, `Style`, `Asset`, `DocList`; enums `Format`, `Block`, `Inline`, `CellSlot`,
`TableKind`, `NoteKind`, `MarkerKind`, `LinkTarget`, `ImageSource`; and error `ConvertError`.

**We use `toMarkdownBytes` and nothing else in v1.** `toDocument` is a future door (per-block
chunking, citations back to a page) — not needed to fix the bug.

`ConvertError` is a real UniFFI error type, so the generated Kotlin **throws**. Variants visible in
the binary include `Unsupported`, `Malformed`, `Encrypted`, `ResourceLimit`, `MissingPart`, `Io`.
That mapping matters for the UI (§13):

| `ConvertError` | Our tier decision | Row state |
|---|---|---|
| `Unsupported` | try OCR → provider | `EXTRACTING` → `NEEDS_PROVIDER` |
| `Malformed` | try OCR (a scanned/broken PDF may still OCR) | as above |
| `Encrypted` | stop — we will not prompt for a password in v1 | `FAILED` |
| `ResourceLimit` | stop | `FAILED` |
| `Io` / anything else | stop | `FAILED` |

### 9.3 What formats it actually covers (confirmed in the binary)

Format modules compiled in: **`docx`, `doc` (legacy binary Word), `pptx`, `ppt` (legacy), `xlsx`/`sheet`
(incl. `workbook.bin`), `odf` (text / spreadsheet / presentation), `rtf`, `epub`, `csv`, `pdf`**.

**The PDF story is better than this plan assumed.** anydoc statically links `pdf-inspector 1.14.2` on
top of `lopdf 0.42.0` — a real text extractor with reading-order reconstruction, table detection,
font/ToUnicode handling and Markdown conversion. It also carries a text-quality classifier whose
states are literally `TextBased`, `ScannedImageBased`, `Mixed`.

Two consequences:

1. **Tier 1 handles digital PDFs well**, including tables — not just a naive text dump.
2. **anydoc itself tells us when a PDF is a scan.** So the Tier 1 → Tier 2 (OCR) hand-off does not
   need our own heuristic ("output too short → assume scan"); prefer anydoc's own signal where the
   Kotlin surface exposes it, and fall back to the length heuristic only if it does not.

It also emits diagnostics we should not surface raw (e.g. "broken font encodings detected; extracted
text may be garbled"). If a warning channel is exposed, log it; never put it in the row.

### 9.4 Generating the Kotlin binding (the only remaining step)

The `.so` is committed but **the generated Kotlin binding is not in the repo yet** — nothing under
`app/src/main/java/uniffi/` exists. Generate it from the shipped library so the two can never drift:

```bash
cargo run --bin uniffi-bindgen generate --library \
  app/src/main/jniLibs/arm64-v8a/libanydoc_kotlin.so \
  --language kotlin --out-dir app/src/main/java
```

This writes `app/src/main/java/uniffi/anydoc_kotlin/anydoc_kotlin.kt`, which calls
`System.loadLibrary("anydoc_kotlin")` itself. **Commit it**, and re-run it only when the `.so` is
rebuilt.

The binding needs `net.java.dev.jna:jna:5.14.0@aar` on the classpath (UniFFI 0.29 Kotlin bindings use
JNA) — add it to `app/build.gradle.kts` and to the version catalog. Confirm the exact requirement from
the generated file's imports before adding, and note it adds ~1 MB.

### 9.5 Kotlin usage (thin, defensive)

`app/src/main/java/com/echoflow/data/extract/Anydoc.kt`

```kotlin
/**
 * Tier 1: the bundled Rust document parser. Never throws — every failure is a null, so the caller
 * can fall through to OCR / provider. Callers must be on Dispatchers.IO.
 */
object Anydoc {
    /** true when the library is present for this device's ABI (see §9.6). */
    val available: Boolean by lazy {
        runCatching { uniffi.anydoc_kotlin.formatFromExtension("txt"); true }.getOrDefault(false)
    }

    sealed interface Result {
        data class Text(val markdown: String) : Result
        /** anydoc declined — the file may still be OCR-able. */
        data object TryOcr : Result
        data class Failed(val reason: String) : Result
    }

    fun convert(bytes: ByteArray, filename: String): Result = try {
        val md = uniffi.anydoc_kotlin.toMarkdownBytes(bytes, filename)   // exact arity per binding
        if (md.isNotBlank()) Result.Text(md) else Result.TryOcr
    } catch (e: uniffi.anydoc_kotlin.ConvertException.Encrypted) {
        Result.Failed("encrypted")
    } catch (e: uniffi.anydoc_kotlin.ConvertException.ResourceLimit) {
        Result.Failed("too large")
    } catch (e: uniffi.anydoc_kotlin.ConvertException) {
        Result.TryOcr                       // Unsupported / Malformed / MissingPart
    } catch (e: UnsatisfiedLinkError) {
        Result.TryOcr                       // .so missing for this ABI — never crash
    } catch (t: Throwable) {
        Result.Failed(t.javaClass.simpleName)
    }
}
```

> Adjust the exact parameter list and exception class names to whatever the generated binding
> declares — read `anydoc_kotlin.kt` once and match it. The shape above (bytes in, Markdown out,
> typed errors) is confirmed; the arity is not.

**Always cap bytes before calling.** The existing `MAX_IMPORT_BYTES` cap in `ProjectManager` already
bounds this, and anydoc has its own internal `max_total_bytes` / `max_entry_bytes` limits that raise
`ResourceLimit` — so a zip-bomb `.docx` is handled on both sides.

### 9.6 ABI: arm64-only, and what that means

Only `arm64-v8a` ships. That is the right call — it covers essentially every real Android phone from
the last several years — but two things follow:

1. **The x86_64 emulator cannot run Tier 1.** `UnsatisfiedLinkError` → `Anydoc.available == false` →
   every file falls through to OCR/provider. Do your on-device verification on an **arm64 emulator
   image or a physical device**, or you will "discover" a bug that does not exist.
2. **Instrumented tests that assert Tier 1 must be arm64-gated** (`assumeTrue(Anydoc.available)`),
   otherwise CI on an x86_64 emulator goes red.

If armeabi-v7a / x86_64 are ever wanted, rebuild with
`cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o app/src/main/jniLibs --platform 24 build --release`
and re-run the binding generation. Not needed now.

### 9.7 Size & packaging

- 7.0 MB, one ABI. Against the app's existing ~165 MB of native libs this is noise.
- No Gradle change is needed: `app/src/main/jniLibs/` is the default source set and the build already
  picks the library up (it appears in `merged_jni_libs` / `stripped_native_libs` for the debug
  variant, so packaging is confirmed working).
- Keep it out of any `packagingOptions` exclusion and do **not** let `jniLibs.useLegacyPackaging`
  flip — the extracted-vs-compressed choice affects nothing functionally here, but changing it
  silently changes APK size.

### 9.8 Rebuild recipe (only if the library must change)

The wrapper crate lives outside this repo. To rebuild:

```bash
rustup target add aarch64-linux-android
cargo install cargo-ndk
cargo ndk -t arm64-v8a -o <repo>/app/src/main/jniLibs --platform 24 build --release
```

Use NDK r26+ so the 16 KB alignment is preserved; verify with
`readelf -l libanydoc_kotlin.so | grep LOAD` (align must be `0x4000`). Then re-generate the Kotlin
binding (§9.4) and re-run the licence generation (§14.1) — the linked Rust dependency tree is what the
notices cover, and it changes with the library.
---

## 10. ML Kit OCR module

**Dependency** (unbundled, decided §16.1):
```kotlin
implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
```
Model downloads on first use via Play Services. Handle "model not yet downloaded / unavailable" as a normal miss (return null → caller falls to Tier 3 or the honest state); surface a one-time "preparing OCR…" hint on the very first OCR.

`app/src/main/java/com/echoflow/data/extract/OcrExtractor.kt` (sketch):
```kotlin
class OcrExtractor(private val context: Context) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** OCR an image file. */
    suspend fun ocrImage(file: File): String? = withContext(Dispatchers.IO) {
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
        recognizeBlocking(InputImage.fromBitmap(bmp, 0)).takeIf { it.isNotBlank() }
    }

    /** OCR a scanned PDF by rendering each page to a bitmap first. */
    suspend fun ocrPdf(file: File, maxPages: Int = 30): String? = withContext(Dispatchers.IO) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val sb = StringBuilder()
                for (i in 0 until minOf(renderer.pageCount, maxPages)) {
                    renderer.openPage(i).use { page ->
                        val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        sb.append(recognizeBlocking(InputImage.fromBitmap(bmp, 0))).append("\n\n")
                    }
                }
                sb.toString().takeIf { it.isNotBlank() }
            }
        }
    }

    private suspend fun recognizeBlocking(img: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(img)
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resume("") }
        }
}
```
Notes: render at ~2× for legible OCR; cap pages; ML Kit calls are async — wrapped as `suspend`.

---

## 11. The orchestrator — `FileExtractor`

A single class decides the tier and writes the result. Called from `ProjectManager.addDocument` (after copy) and from the backfill sweep.

`app/src/main/java/com/echoflow/data/extract/FileExtractor.kt` (sketch):
```kotlin
class FileExtractor(private val context: Context, private val ocr: OcrExtractor) {

    data class Result(val text: String?, val status: ExtractionStatus, val tier: ExtractionTier?)

    suspend fun extract(file: File, mime: String, name: String): Result = withContext(Dispatchers.IO) {
        val ext = name.substringAfterLast('.', "").lowercase()

        // Images never go to anydoc — straight to OCR.
        if (mime.startsWith("image/")) {
            val t = ocr.ocrImage(file)
            return@withContext if (t != null) Result(t, EXTRACTED, ExtractionTier.OCR)
                               else Result(null, NEEDS_PROVIDER, null)
        }

        // Tier 1: anydoc for structured docs + digital PDFs.
        if (ext in ANYDOC_EXTS || mime == "application/pdf") {
            Anydoc.toMarkdownOrNull(file.readBytes(), name)?.let {
                return@withContext Result(it, EXTRACTED, ExtractionTier.ANYDOC)
            }
        }

        // Tier 2: scanned PDF → OCR.
        if (mime == "application/pdf" || ext == "pdf") {
            ocr.ocrPdf(file)?.let {
                return@withContext Result(it, EXTRACTED, ExtractionTier.OCR)
            }
        }

        // Plain text that anydoc/OCR skipped (legacy path) — keep the old reader as a floor.
        legacyPlainText(file, mime, name)?.let {
            return@withContext Result(it, EXTRACTED, ExtractionTier.LEGACY_TEXT)
        }

        // Nothing local worked → let the model try per message, if it can.
        Result(null, NEEDS_PROVIDER, null)
    }

    companion object {
        private val ANYDOC_EXTS = setOf(
            "doc","docx","docm","ppt","pptx","xls","xlsx","xlsm","xlsb",
            "odt","ods","odp","rtf","epub","csv"
        )
    }
}
```
`ProjectManager.addDocument` change: after a successful copy, call `extract(...)`, then insert the row with `extractedText`, `extractionStatus`, `extractionTier`. On failure, insert with `PENDING`/`NEEDS_PROVIDER` — never block the import.

Guard huge files: keep the existing `MAX_IMPORT_BYTES` cap; add a byte cap before `file.readBytes()` for anydoc so a 25 MB file doesn't balloon memory (stream or cap).

---

## 12. Injection changes (Tiers 1–2 need almost none)

`buildSystemContext` already appends every `hasText` doc. Because Tiers 1–2 write `extractedText`, **they light up for free.** One clarity tweak — filter by status so a half-processed row doesn't inject an empty string:
```kotlin
val docs = projectDocumentDao.getForProjectSync(projectId)
    .filter { it.status == ExtractionStatus.EXTRACTED && it.hasText }
```

**Tier 3 injection** happens in the message-send path (`ChatViewModel`, near where `localAttachment*` messages are built), not in `buildSystemContext`:
```kotlin
// pseudo: when sending in a project chat and the model supports files/vision
val providerDocs = projectDocumentDao.getForProjectSync(projectId)
    .filter { it.status == ExtractionStatus.NEEDS_PROVIDER }
if (providerDocs.isNotEmpty() && modelSupportsFiles(activeModel)) {
    // attach each as an extra content part using the existing file/image encoders
}
```
Confirm the capability check against `OpenRouterModelInfo` (search for `modalities`/vision). If the app can only attach one file per message today, either extend the encoder to a list or attach the first N and note the limit in the UI.

---

## 13. UI & design guide (Files screen) — how parsing looks without breaking the premium feel

This is the part users actually see. The rule for the whole feature: **extraction is background work
that the row narrates quietly.** No dialogs, no full-screen spinners, no blocking the picker, no
banner. The file row itself is the progress UI.

### 13.1 Principles (non-negotiable)

1. **The row is the loader.** A file appears in the list *immediately* on pick, in a `PENDING` state,
   and settles in place. Nothing else on the screen moves or dims.
2. **Optimistic and never blocking.** The user can add a second file, scroll, or leave the screen
   while extraction runs. Room flows push the state change; the UI never polls or awaits.
3. **One line of secondary text, one accent.** The status lives in the existing supporting line under
   the filename — same `labelSmall`, same `onSurfaceVariant`. We change *words and one glyph*, never
   the row's height, shape, or padding. Rows must not resize between states, or the grouped-shape
   list will jitter.
4. **Roles only.** `onSurfaceVariant` for calm states, `error` only for genuine failure,
   `onSecondaryContainer` for the active indicator. No hex, so it reads right in all six palettes plus
   dynamic colour, light and dark.
5. **Motion is expressive but small.** Cross-fade the supporting line and the leading glyph; never
   animate the row's bounds.
6. **Honest language.** "Reading…", silence on success, "Couldn't read this file". No percentages we
   can't honour, no fake progress bars for a 5 ms parse.

### 13.2 States

`ProjectDocument.status` drives everything. Five states, exactly:

| Status | Leading slot | Supporting line | Colour |
|---|---|---|---|
| `PENDING` (queued / copying) | file glyph | `12.4 MB · Reading…` | `onSurfaceVariant` |
| `EXTRACTING` (anydoc / OCR running) | `LoadingIndicator` 18 dp in place of the glyph | `12.4 MB · Reading…` (OCR: `· Reading page 3 of 12`) | `onSurfaceVariant` |
| `EXTRACTED` | file glyph | `12.4 MB` — nothing else (success is silent) | `onSurfaceVariant` |
| `NEEDS_PROVIDER` | file glyph | `12.4 MB · Sent to the model as a file` | `onSurfaceVariant` |
| `FAILED` | file glyph | `12.4 MB · Couldn't read this file` | `error` |

Success being silent is deliberate: a list where every row shouts "✓ Extracted" is noise. The absence
of a hint *is* the success signal, exactly as today's rows read.

The one loud-ish case: `NEEDS_PROVIDER` **and** the chat's active model is text-only → the supporting
line becomes `This model can't read this file` in `error`. That is a real, actionable problem, so it
earns the accent.

### 13.3 The leading slot — where the loader lives

Today the row's leading slot is a 34 dp `RoundedCornerShape(11.dp)` box on `secondaryContainer`
holding an 18 dp `Icons.Default.Description`
([ProjectsScreens.kt:798](app/src/main/java/com/echoflow/ui/screens/ProjectsScreens.kt:798)).

**Keep the box. Swap only its contents.** The M3 Expressive `LoadingIndicator` — the morphing-shape
mark the app already uses in [SearchTimeline.kt:86](app/src/main/java/com/echoflow/ui/components/SearchTimeline.kt:86)
and in the composer send slot — is exactly right here: parsing then reads as the same family of
activity as a running search or a streaming reply, rather than a new widget the app has never shown.

```kotlin
Box(
    Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
        .background(MaterialTheme.colorScheme.secondaryContainer),
    contentAlignment = Alignment.Center,
) {
    AnimatedContent(
        targetState = document.status.isBusy,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        label = "docLeading",
    ) { busy ->
        if (busy) {
            LoadingIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            Icon(
                Icons.Default.Description, null, Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
```

The indicator is tinted `onSecondaryContainer`, not `primary` — it sits *inside* the secondary
container, and using that container's own on-colour is what stops it looking like a bolted-on progress
widget. Size it 18 dp so it takes the exact optical weight of the icon it replaces.

### 13.4 The supporting line

```kotlin
val hint = when (document.status) {
    PENDING, EXTRACTING ->
        if (document.ocrPage != null) "Reading page ${document.ocrPage} of ${document.ocrPages}"
        else "Reading…"
    EXTRACTED      -> null
    NEEDS_PROVIDER -> if (modelReadsFiles) "Sent to the model as a file"
                      else "This model can't read this file"
    FAILED         -> "Couldn't read this file"
}
```

Render as `formatBytes(size)` + `" · "` + hint, and cross-fade **the text only**:

```kotlin
AnimatedContent(hint, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) })
```

Never `AnimatedVisibility` the whole line — that changes row height. The line always exists; only its
tail changes.

### 13.5 Entry motion

A newly picked file should *arrive*, not pop:

- The `LazyColumn` already keys by `doc.id`; add `Modifier.animateItem()` so an insertion slides the
  rest of the list with the theme's spatial spring instead of jumping.
- The new row fades 0 → 1 over ~200 ms while already showing `PENDING`. The user sees the file land
  already reading itself. That single beat is the whole "premium" impression — get it right and no
  further ornament is needed.

### 13.6 What we do *not* do

- ❌ No modal / `AlertDialog` during import, and no full-screen spinner overlay.
- ❌ No determinate progress bar for anydoc — the parse is single-digit milliseconds and a bar that
  fills and vanishes instantly looks broken. Only OCR gets a page counter, and only because it is
  genuinely slow and genuinely countable.
- ❌ No toast/snackbar on success. Snackbar stays reserved for *import* failure (unreadable file, over
  the size cap), which is the existing behaviour.
- ❌ No new colours, corner radii, or paddings. Everything above reuses `Spacing`, `groupedItemShape`,
  and existing colour roles.
- ❌ No layout shift. Verify by adding a file and confirming rows below do not move by a pixel when a
  row flips `EXTRACTING → EXTRACTED`.

### 13.7 Empty-state and header copy

`FilesEmptyState` currently says *"Add text files — notes, briefs, transcripts…"*. That stops being
true the moment this ships — update it:

> **No files yet**
> Add PDFs, Word or Excel files, slides or notes — EchoFlow reads them on your device and makes them
> background knowledge for this project.

The Files header subtitle stays `"N files"`, but while any row is busy show `"N files · reading 1"` in
the same `labelLarge` / `onSurfaceVariant`. One word, no new component.

### 13.8 Accessibility

- While busy, the leading box carries `semantics { contentDescription = "Reading ${document.name}" }`,
  so TalkBack announces what the sighted user reads.
- Status is never colour-only: every state carries words. `FAILED` is red **and** says
  "Couldn't read this file".
- `LoadingIndicator` keeps animating even at animator-duration-scale 0 (the reason it is used in the
  composer — see the note at
  [EchoToolComponents.kt:690](app/src/main/java/com/echoflow/ui/components/EchoToolComponents.kt:690)),
  so no special-casing is needed for reduced-motion devices.

### 13.9 Screenshots owed on the PR (repo rule)

Capture on the emulator: (1) a row mid-`EXTRACTING` with the loading glyph, (2) the settled list of
mixed types (PDF + docx + xlsx) all `EXTRACTED`, (3) a `FAILED` row, (4) the updated empty state.
Commit under `docs/screenshots/project-file-extraction/`.

---

## 14. Echo Labs → Open-Source Licenses page (license compliance)

MIT requires the copyright + license text to travel with the binary. Because the `.so` statically links anydoc **and its whole Rust dependency tree**, attribute all of it, generated automatically.

### 14.1 Generate the notices (one-time, re-run on version bump)

In the wrapper crate:
```bash
cargo install cargo-about
cargo about init            # creates about.toml (allow MIT, Apache-2.0, BSD, etc.)
cargo about generate about.hbs > ../../app/src/main/assets/licenses/third_party_rust.txt
```
Also drop anydoc's own `LICENSE` at `native/anydoc-android/anydoc-LICENSE` (or rely on `cargo-about`, which includes it).

### 14.2 Add the page (follows the existing Echo Labs page pattern)

1. **Page id + route** — in `SettingsScreen.kt`, where `PageEchoLabs -> EchoLabsPage(...)` is handled, add a constant (e.g. `PageLicenses`) and a branch:
   ```kotlin
   PageLicenses -> OpenSourceLicensesPage(onBack = navigateBack)
   ```
2. **Entry row** — in `EchoLabsPage` (`SettingsCustomProviders.kt`), add a row that calls `onOpen(PageLicenses)`. (Or place it on the main Settings list under an "About" group — designer's call.)
3. **The page** — new composable using the shared scaffold:
   ```kotlin
   @Composable
   internal fun OpenSourceLicensesPage(onBack: () -> Unit) {
       val context = LocalContext.current
       val text = remember {
           context.assets.open("licenses/third_party_rust.txt").bufferedReader().use { it.readText() }
       }
       SettingsPageScaffold(title = "Open-source licenses",
           subtitle = "Third-party components", onBack = onBack) {
           Surface(shape = MaterialTheme.shapes.large,
               color = MaterialTheme.colorScheme.surfaceContainer,
               modifier = Modifier.fillMaxWidth()) {
               Text(text, style = MaterialTheme.typography.bodySmall,
                   fontFamily = FontFamily.Monospace, modifier = Modifier.padding(Spacing.base))
           }
       }
   }
   ```
4. **Navigation test** — `SettingsNavigationTest.echoLabsPagesReturnToEchoLabs` enumerates Echo Labs pages; add the new page so its back-behaviour is covered.

### 14.3 What we do **not** owe

No repo link, no "features used" list, no open-sourcing our app. Retaining the notice (the generated file) is the whole obligation. ML Kit (Apache-2.0 via Google Play Services) is covered by Play Services terms; if bundled, add its notice to the same file.

---

## 15. Threading, performance, size

- All extraction on `Dispatchers.IO`; import returns immediately with a `PENDING` row that flips to `EXTRACTED` when done (Room flow updates the UI live).
- anydoc ~<5 ms; OCR is the slow one (bounded by page cap). Show progress.
- Peak memory: cap bytes before `readBytes()`; cap OCR pages; render bitmaps at a sane scale and recycle.
- APK: anydoc `.so` per ABI (few MB each) + optional bundled ML Kit (~4 MB). Prefer unbundled ML Kit + App Bundle ABI splits to keep per-device download small.

---

## 16. Decisions

### 16.1 Locked (decided 2026-08-18)

1. **Scope = Option C — all three tiers** (anydoc + on-device OCR + provider fallback). The full pipeline ships in v1.
2. **ML Kit = unbundled** (`com.google.android.gms:play-services-mlkit-text-recognition`, ~0 MB). **Consequence to handle in code:** the OCR model downloads on first use via Google Play Services, so (a) the **first** OCR needs network once and may briefly report "preparing OCR…", and (b) on **de-Googled devices (no Play Services)** Tier 2 will fail — those files must fall straight through to Tier 3 (provider) or the honest "can't read locally" state. `OcrExtractor` must treat "model not available / download failed" as a normal miss, not a crash.
3. **Backfill = lazy** — re-extract a project's legacy files when that project is next opened/used, not in an upgrade-time sweep.

### 16.2 Still to confirm during the build (engineering, not product)

4. ~~**anydoc public API shape**~~ — **resolved.** The entry point is `toMarkdownBytes` (bytes → Markdown, throws `ConvertError`); the library is already built and committed. Only the exact generated parameter list remains to be read off the binding (§9.2, §9.4).
5. **Model capability signal for Tier 3** — where does the app already know a model accepts files/vision (`OpenRouterModelInfo`)? Reuse it; if absent, default to "don't attach" and say so in the UI.
6. **One vs many attachments per message** — does the current encoder allow multiple `file`/`image_url` parts? If not, extend it or cap.

---

## 17. Build checklist (do these in order)

0. ~~**Spike anydoc first.**~~ **DONE.** `libanydoc_kotlin.so` (arm64-v8a, UniFFI 0.29.5, 16 KB-aligned) is built, committed, and verified working in a separate Android app. The remaining anydoc work is only §9.4 (generate + commit the Kotlin binding, add the JNA dependency) and §9.5 (the defensive wrapper) — do those first, since everything else depends on the call shape.
1. **DB:** add the two columns to `ProjectDocument`, add `ExtractionStatus`/`ExtractionTier`, bump `@Database` to 23, add `MIGRATION_22_23`, register it, commit the exported `23.json`, update the two migration tests. Build & run the DB tests — **green before moving on**.
2. **anydoc binding:** run the UniFFI generation against the committed `.so` (§9.4), commit `app/src/main/java/uniffi/anydoc_kotlin/anydoc_kotlin.kt`, add the JNA dependency, and write `Anydoc.kt` (§9.5). Add an instrumented test that converts a sample `.docx` **and** a digital PDF to non-empty Markdown — gated on `assumeTrue(Anydoc.available)` so it skips on x86_64 emulators (§9.6).
3. **OcrExtractor** (if Tier 2 in scope): add the dependency, implement image + PDF OCR.
4. **FileExtractor:** implement the tier logic; wire into `ProjectManager.addDocument`; add the backfill sweep.
5. **Injection:** filter `buildSystemContext` by status; add Tier-3 attachment in the send path behind the capability gate.
6. **UI:** per-row status in `DocumentRow`; the Open-Source Licenses page + route + entry row + nav test.
7. **Licenses:** `cargo about` output committed to `assets/licenses/`.
8. **Verify on device:** attach a digital PDF (Tier 1), a scanned PDF/image (Tier 2), and — with a text-only model — confirm the honest "can't read" state; with a vision model confirm Tier 3 attaches. Capture emulator screenshots for the PR (repo rule).

---

## 18. Why this shape (one paragraph)

It fixes the real files people attach (Word/Excel/PDF) **locally and free** with structure-preserving Markdown; it degrades gracefully to on-device OCR for scans/images and only to the paid provider path for the genuinely un-readable, gated by model capability; it serves on-device models that can never take files; it costs **zero per message** after a one-time extraction; and it upgrades every existing database with two nullable columns and no data movement. The native library is a **one-time build** committed as a blob, so CI stays Rust-free, and the MIT/third-party obligations are met by an auto-generated, in-app licenses page.
```
