# ComparisonTool

A desktop application for comparing files and folders side-by-side, built with Java 25 and JavaFX 21.

---

## Features

- **File comparison** — side-by-side diff with line-level and intra-line highlighting, synchronized scrolling, merge controls, undo/redo, and in-place editing
- **Folder comparison** — recursive tree diff with status icons, drill-down to file view, copy/delete/ignore actions, and progress/cancellation
- **Encoding-aware** — auto-detects charset and BOM; preserves encoding and line endings (LF/CRLF) on save
- **Flexible matching** — size-only, size+timestamp (with tolerance), or content (SHA-256 hash)
- **Ignore rules** — glob include/exclude masks, per-item manual ignores, and content-level options (whitespace, case, line endings)
- **Persistence** — settings, recent comparisons, and named filter profiles saved as JSON
- **Theming** — Light/Dark/System via AtlantaFX; custom highlight colours
- **Accessible** — keyboard shortcuts for all major actions; accessible names on all interactive controls
- **Self-contained packaging** — `jpackage` app image (no separate JRE installation needed)

---

## Requirements

| Requirement | Version |
|---|---|
| JDK | 25 (e.g. [Microsoft Build of OpenJDK 25](https://learn.microsoft.com/en-us/java/openjdk/download#openjdk-25)) |
| OS | Windows 10+, macOS 12+, Ubuntu 20.04+ |
| Disk | ~500 MB (build outputs land in `C:\BuildTemp\GUIComparisonApp` on Windows by default — see [Build output location](#build-output-location)) |

No separate Maven or Gradle installation is needed — the project ships a Gradle wrapper (`gradlew` / `gradlew.bat`).

---

## Project structure

```
comparison-tool/
├── model/        # Domain records and enums (no JavaFX, no I/O)
├── infra/        # File I/O, encoding detection, hashing, persistence, concurrency
├── core/         # Diff engines, merge manager, comparison service
├── viewmodel/    # JavaFX observable ViewModels (javafx.base only — no Node subclasses)
├── ui/           # JavaFX views, cells, controls (TestFX integration tests)
└── app/          # Entry point, DI wiring, jpackage target, end-to-end tests
```

Module dependency order: `model` ← `infra` ← `core` ← `viewmodel` ← `ui` ← `app`

---

## Building

### 1. Set JAVA_HOME

```powershell
# Windows (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot"
```

```bash
# macOS / Linux
export JAVA_HOME=/path/to/jdk-25
```

### 2. Compile and run all tests

```powershell
.\gradlew.bat build --no-daemon          # Windows
./gradlew     build --no-daemon          # macOS / Linux
```

This compiles all modules, runs all JUnit 5 and TestFX tests, and produces the module JARs.

### 3. Run just the tests for a specific module

```powershell
.\gradlew.bat :ui:test   --no-daemon     # UI + TestFX tests only
.\gradlew.bat :core:test --no-daemon     # Core diff-engine unit tests
.\gradlew.bat :app:test  --no-daemon     # End-to-end integration tests
```

### 4. Run the application from source

```powershell
.\gradlew.bat :app:run --no-daemon
```

---

## Build output location

The root `build.gradle.kts` redirects all module build outputs **outside OneDrive** to avoid file-lock issues with the VS Code Java Language Server:

```
C:\BuildTemp\GUIComparisonApp\
├── app\          ← app module outputs (JARs, install, jpackage image)
├── core\
├── infra\
├── model\
├── ui\
└── viewmodel\
```

Test reports are written to `C:\BuildTemp\GUIComparisonApp\<module>\reports\tests\test\index.html`.

To change the base path, edit the `buildBase` variable at the top of `build.gradle.kts`.

---

## Packaging

### Build the self-contained app image

```powershell
.\gradlew.bat :app:packageAppImage --no-daemon
```

Output: `C:\BuildTemp\GUIComparisonApp\app\jpackage\ComparisonTool\` (Windows)

The image bundles its own JRE — no JDK installation is required on the target machine.

Platform | Binary location
---|---
Windows | `ComparisonTool\ComparisonTool.exe`
macOS   | `ComparisonTool.app\Contents\MacOS\ComparisonTool`
Linux   | `ComparisonTool\bin\ComparisonTool`

### Validate the packaged binary (headless smoke test)

```powershell
.\gradlew.bat :app:validatePackageImage --no-daemon
```

This runs the packaged binary with `--smoke-test`, which performs a file and folder comparison entirely in-process (before JavaFX starts) and exits with code 0 on success. Used in CI to confirm the self-contained runtime works.

You can also invoke it directly:

```powershell
# Windows
C:\BuildTemp\GUIComparisonApp\app\jpackage\ComparisonTool\ComparisonTool.exe --smoke-test
```

### Clean the previous app image

```powershell
.\gradlew.bat :app:cleanPackageAppImage --no-daemon
```

---

## Using the application

### Launching

**From the packaged app image** (after running `packageAppImage`):

```powershell
# Windows
C:\BuildTemp\GUIComparisonApp\app\jpackage\ComparisonTool\ComparisonTool.exe

# macOS
open C:\BuildTemp\GUIComparisonApp\app\jpackage\ComparisonTool.app

# Linux
C:/BuildTemp/GUIComparisonApp/app/jpackage/ComparisonTool/bin/ComparisonTool
```

**Directly from source** (no packaging step needed):

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot"
.\gradlew.bat :app:run --no-daemon
```

The application opens with a **Welcome / launcher screen** that lets you pick File Comparison or Folder Comparison, or re-open a recent pair.

---

### Comparing two files

1. From the launcher, choose **File Comparison** (or use **File → New File Comparison** from the menu bar).
2. Click the **left folder icon** and browse to the first file (or type the path directly into the path bar).
3. Click the **right folder icon** and browse to the second file.
4. Press **F5** (or click **Compare**) to run the diff.

The view shows both files side-by-side with coloured highlights:

| Colour | Meaning |
|---|---|
| Red / pink | Line exists only on the left (deleted) |
| Green | Line exists only on the right (added) |
| Yellow / orange | Line exists on both sides but content differs |
| Darker inline highlight | Exact characters that differ within a changed line |

**Navigating differences**

- `F7` — jump to the next difference block
- `Shift+F7` — jump to the previous difference block
- The difference counter in the toolbar shows `N / total`.

**Merging changes**

- Click the **→** arrow next to a diff block to copy that block from left to right.
- Click the **←** arrow to copy from right to left.
- `Ctrl+Right` / `Ctrl+Left` — copy the currently selected block via keyboard.
- `Ctrl+Shift+Right` / `Ctrl+Shift+Left` — copy **all** diff blocks in one direction.

**Editing**

Both panels are editable in place. Changes are marked with a pencil icon in the tab header.

**Saving**

- `Ctrl+S` — save both dirty files.
- The save dialog respects the original encoding and line endings (LF/CRLF) detected on load.

**Undo / redo**

- `Ctrl+Z` — undo the last edit or merge.
- `Ctrl+Y` — redo.

**Ignore options** (toolbar or **Options** menu)

- Ignore whitespace differences
- Ignore case differences
- Ignore line-ending differences (treats LF and CRLF as equal)

---

### Comparing two folders

1. From the launcher, choose **Folder Comparison** (or **File → New Folder Comparison**).
2. Set the **left** and **right** root directories using the path bars or folder icons.
3. Press **F5** (or click **Compare**) to start the recursive scan.

A progress bar and cancellation button appear while the scan runs. Large trees can be cancelled at any time with the **×** button or `Escape`.

**Reading the results tree**

Each row represents a file or subdirectory. The **Status** column shows:

| Icon / label | Meaning |
|---|---|
| `=` (grey) | Identical on both sides |
| `≠` (orange) | Content differs |
| `←` (blue) | Exists on left only |
| `→` (blue) | Exists on right only |
| `?` (yellow) | Ignored (manually or by filter) |

Folders are shown collapsed by default; click the triangle or press the right arrow key to expand.

**Drilling down to a file diff**

Double-click any non-identical file row to open it in a File Comparison panel. The back button (or closing the tab) returns to the folder tree.

**Copying files**

- Right-click a row → **Copy to right** / **Copy to left** to sync a single file.
- Select multiple rows with `Ctrl+Click` or `Shift+Click`, then right-click → **Copy selection to right/left**.

**Deleting files**

Right-click → **Delete on left** / **Delete on right**. You will be asked to confirm before any deletion.

**Ignoring items**

Right-click → **Ignore** to hide the selected rows from the diff result. Ignored items can be restored via **View → Show Ignored Items**.

**Filter profiles**

Use **Options → Filter Profiles** to create named sets of include/exclude glob masks (e.g. `*.class`, `target/**`) that are applied before the comparison runs. Profiles are saved to disk and appear in the toolbar dropdown for quick switching.

**Matching strategy** (Options → Comparison Options)

| Strategy | How equality is determined |
|---|---|
| Size only | Files are equal if byte sizes match |
| Size + timestamp | Size must match and modification times must be within the configured tolerance |
| Content (SHA-256) | Full content hash — most accurate, slower on large trees |

---

## Keyboard shortcuts

### Global

| Shortcut | Action |
|---|---|
| `F5` | Run comparison |

### File comparison view

| Shortcut | Action |
|---|---|
| `F7` / `Shift+F7` | Next / previous difference |
| `Ctrl+S` | Save dirty documents |
| `Ctrl+Shift+Right` | Copy all diffs left → right |
| `Ctrl+Shift+Left` | Copy all diffs right → left |
| `Ctrl+Right` | Copy selected block left → right |
| `Ctrl+Left` | Copy selected block right → left |

---

## CI

The `.github/workflows/ci.yml` workflow runs on every push/PR on **Windows, macOS, and Ubuntu** with Java 25 (Zulu distribution):

1. `./gradlew build` — compile + all tests
2. `./gradlew packageAppImage` — produce the self-contained app image
3. `./gradlew validatePackageImage` — run the headless smoke test against the packaged binary
4. Upload test reports and Windows app image as build artifacts

---

## Technology stack

| Component | Library / Tool | Version |
|---|---|---|
| Language | Java | 25 |
| UI framework | JavaFX | 21.0.4 |
| UI theme | AtlantaFX | 2.0.1 |
| Diff engine | java-diff-utils (Myers) | 4.12 |
| JSON persistence | Jackson Databind | 2.17.2 |
| Encoding detection | juniversalchardet | 2.4.0 |
| Build tool | Gradle (Kotlin DSL) | 9.0.0 |
| Unit testing | JUnit 5 + AssertJ + Mockito | 5.10.3 / 3.26.3 / 5.14.0 |
| UI testing | TestFX (headless via Monocle) | 4.0.18 |
| Packaging | jpackage (JDK built-in) | — |

---

## Development tips

- **Avoid running Gradle with `--daemon`** when building on a machine where the workspace is on a synced drive (OneDrive, Dropbox) — use `--no-daemon` to prevent file-lock races.
- **ViewModels are JavaFX-free in the test sense** — `viewmodel` only uses `javafx.base` (property types), not `javafx.controls` or any `Node` subclass, so ViewModel unit tests run without a display server.
- **TestFX tests** in the `ui` module use the Monocle headless glass implementation and require no display. The `@ExtendWith` order must be `{MockitoExtension.class, ApplicationExtension.class}` — Mockito first.
- **Mockito configuration** — the `ui` module uses `ProxyMockMaker` (configured in `ui/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`) to avoid Mockito's byte-buddy dependency issues with JPMS.
