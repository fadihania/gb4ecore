# gh4ecore

`gh4ecore` is a Java 21 command-line collector for building a research dataset of Ecore models from public GitHub repositories.

The app searches GitHub for `.ecore` files, inspects candidate repositories, and writes matching model metadata to CSV. It is intended for Model-Driven Engineering research workflows where models need enough repository history to support empirical analysis.

## Requirements

- JDK 21
- Maven
- A GitHub token exposed as `GITHUB_TOKEN`

The token is required because GitHub code search and repository inspection hit API rate limits quickly.

## Running

Set your token, then run the app with Maven:

```powershell
$env:GITHUB_TOKEN="your-token"
mvn exec:java
```

For a small bounded run:

```powershell
mvn exec:java -Dexec.args="--limit 5"
```

Run tests with:

```powershell
mvn test
```

## CLI Options

All options are optional.

```text
--out <path>           Output directory. Default: out
--min-size <bytes>     Minimum .ecore file size. Default: 10240
--min-commits <count>  Minimum file-level commit count. Default: 10
--size-step <bytes>    Initial GitHub search size bucket width. Default: 10240
--limit <count>        Stop after accepting this many new models.
```

The default size threshold means files must be at least 10 KB.

## Matching Criteria

A model is accepted when all of these are true:

- The file path ends with `.ecore`.
- The file size is at least `--min-size`.
- The file has at least `--min-commits` commits touching that exact file path.

Before inspecting all models in a repository, the app performs a fast repository-level commit precheck. If a repository has fewer total commits than `--min-commits`, it is excluded immediately because no file in that repository can meet the file-level history requirement.

## Outputs

All runtime outputs are written under the configured `--out` directory.

```text
out/
  ecore-models.csv
  excluded-repositories.txt
  unclassified-repositories.csv
  stats.log
```

### `ecore-models.csv`

Contains one row per accepted model:

```csv
model_file_name,repository_name,repository_url,full_file_url,file_size,commits
```

The `commits` column is the total number of commits touching that file path, not just the minimum threshold.

The app records the GitHub file name in `model_file_name`. Model contents are not downloaded.
Resume detection checks both the exact GitHub file URL and a normalized `repository + path` key, so branch and commit-SHA URLs for the same file path are treated as the same model.

### `excluded-repositories.txt`

Contains repositories that were inspected and proven not to contain any model matching the configured criteria.

Repositories are not added to this file when they cannot be safely classified, such as when GitHub returns a truncated tree, the repository cannot be loaded, or a run stops early due to `--limit`.
Transient GitHub API failures, including Hub4j messages such as HTTP response code `-1`, are also treated as unclassified repository failures. The collector logs the reason and continues with later repositories.

### `unclassified-repositories.csv`

Contains repositories that could not be fully classified:

```csv
repository_name,reason
```

These repositories are not treated as excluded. Reasons include truncated repository trees, missing default branches, transient GitHub API failures, and interrupted runs due to `--limit`.

### `stats.log`

Contains run-level diagnostics and is overwritten each time the app starts. It records the active config, each GitHub code-search size bucket, bucket result counts and warnings, bucket-level stats, and final totals.

Final stats include matching model files found during the run. This count includes both newly recorded models and matching files already present in `ecore-models.csv`.

## Resume Behavior

The app is resumable:

- Existing file URLs in `ecore-models.csv` are skipped.
- Repositories in `excluded-repositories.txt` are skipped.
- Newly accepted models are appended to CSV immediately.
- Repositories proven to have no matching model are appended to `excluded-repositories.txt` immediately.
- Repositories that cannot be fully classified are appended to `unclassified-repositories.csv` immediately.

Existing CSV rows are not rewritten.

## Repository Outcomes

Each inspected repository ends with an outcome log:

```text
Repository outcome: EXCLUDED | ecoreFiles=3, skippedSmall=2, skippedFewCommits=1, alreadyRecorded=0, newlyAccepted=0
```

Outcomes:

- `ACCEPTED`: one or more new matching models were written to CSV.
- `ALREADY_RECORDED`: matching model URL(s) already exist in CSV.
- `EXCLUDED`: the repository was fully inspected and no model matched the criteria.
- `SKIPPED_UNCLASSIFIED`: the repository could not be fully classified.

## GitHub API Notes

GitHub code search is rate limited and can cap broad searches. The app starts with size ranges based on `--size-step`, then adaptively splits any range that reports at least 1,000 results until the range is below the cap or has narrowed to a single byte size. GitHub may still return incomplete or capped result sets.

Search ranges use queries shaped like `extension:ecore size:X..Y`. Each code-search file result is checked directly before the repository tree is inspected, and repository tree inspection still runs once per repository to find additional default-branch models.

Hub4j waits internally for some GitHub rate and abuse limits, so the app can appear quiet while an API call is waiting. The slowest step is usually counting commits for a candidate file, because the app iterates the full commit history for that path.

## Audit Verification

The helper script `tools/verify-missing-models.ps1` compares an audit CSV such as `out/missing-in-ecore-models.csv` against `out/ecore-models.csv` and, when `GITHUB_TOKEN` is set, verifies each missing row against GitHub.

```powershell
$env:GITHUB_TOKEN="your-token"
.\tools\verify-missing-models.ps1
```

## Scope

`gh4ecore` collects and catalogs Ecore model files. It does not currently validate Ecore files with EMF, generate Java code from models, or compile models in the EMF code-generation sense.
