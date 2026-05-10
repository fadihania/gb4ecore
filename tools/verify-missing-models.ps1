param(
    [string]$MissingCsv = "out/missing-in-ecore-models.csv",
    [string]$CatalogCsv = "out/ecore-models.csv",
    [string]$ExcludedRepositories = "out/excluded-repositories.txt",
    [string]$UnclassifiedRepositories = "out/unclassified-repositories.csv",
    [int]$MinimumSize = 10240,
    [int]$MinimumCommits = 10
)

$ErrorActionPreference = "Stop"

function Get-GitHubFileKey {
    param([string]$Url)
    if ($Url -match '^https://github\.com/([^/]+/[^/]+)/blob/[^/]+/(.+)$') {
        return ($matches[1] + "|" + $matches[2]).ToLowerInvariant()
    }
    return $Url.ToLowerInvariant()
}

function Get-GitHubBlobParts {
    param([string]$Url)
    if ($Url -notmatch '^https://github\.com/([^/]+/[^/]+)/blob/([^/]+)/(.+)$') {
        throw "Unsupported GitHub blob URL: $Url"
    }
    [pscustomobject]@{
        Repository = $matches[1]
        Ref = $matches[2]
        Path = $matches[3]
    }
}

function Invoke-GitHubJson {
    param(
        [string]$Uri,
        [string]$Token
    )
    Invoke-RestMethod `
        -Uri $Uri `
        -Headers @{
            "Authorization" = "Bearer $Token"
            "Accept" = "application/vnd.github+json"
            "X-GitHub-Api-Version" = "2022-11-28"
            "User-Agent" = "gh4ecore-verifier"
        }
}

function Test-GitHubContentExists {
    param(
        [string]$Repository,
        [string]$Path,
        [string]$Ref,
        [string]$Token
    )
    $escapedPath = [uri]::EscapeDataString($Path).Replace("%2F", "/")
    $escapedRef = [uri]::EscapeDataString($Ref)
    $uri = "https://api.github.com/repos/$Repository/contents/$escapedPath" + "?ref=$escapedRef"
    try {
        Invoke-GitHubJson -Uri $uri -Token $Token
    } catch {
        $null
    }
}

function Count-GitHubPathCommitsAtLeast {
    param(
        [string]$Repository,
        [string]$Path,
        [int]$Minimum,
        [string]$Token
    )
    $count = 0
    $page = 1
    $escapedPath = [uri]::EscapeDataString($Path)
    while ($count -lt $Minimum) {
        $uri = "https://api.github.com/repos/$Repository/commits?path=$escapedPath&per_page=100&page=$page"
        $commits = Invoke-GitHubJson -Uri $uri -Token $Token
        if ($null -eq $commits -or $commits.Count -eq 0) {
            break
        }
        $count += $commits.Count
        if ($commits.Count -lt 100) {
            break
        }
        $page++
    }
    $count
}

$missing = Import-Csv $MissingCsv
$catalog = Import-Csv $CatalogCsv

$catalogExact = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$catalogPath = [System.Collections.Generic.HashSet[string]]::new()
$catalogRepos = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$catalog | ForEach-Object {
    [void]$catalogExact.Add($_.full_file_url)
    [void]$catalogPath.Add((Get-GitHubFileKey $_.full_file_url))
    [void]$catalogRepos.Add($_.repository_name)
}

$excluded = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
if (Test-Path $ExcludedRepositories) {
    Get-Content $ExcludedRepositories |
        Where-Object { $_ -and -not $_.StartsWith("#") } |
        ForEach-Object { [void]$excluded.Add($_.Trim()) }
}

$unclassified = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
if (Test-Path $UnclassifiedRepositories) {
    Import-Csv $UnclassifiedRepositories |
        ForEach-Object { [void]$unclassified.Add($_.repository_name) }
}

$localSummary = [pscustomobject]@{
    MissingRows = $missing.Count
    CatalogRows = $catalog.Count
    ExactUrlMatches = ($missing | Where-Object { $catalogExact.Contains($_.file_url) }).Count
    NormalizedPathMatches = ($missing | Where-Object { $catalogPath.Contains((Get-GitHubFileKey $_.file_url)) }).Count
    MissingRepos = ($missing.repository_name | Sort-Object -Unique).Count
    MissingReposInCatalog = ($missing | Where-Object { $catalogRepos.Contains($_.repository_name) } | Select-Object -ExpandProperty repository_name -Unique).Count
    MissingReposInExcluded = ($missing | Where-Object { $excluded.Contains($_.repository_name) } | Select-Object -ExpandProperty repository_name -Unique).Count
    MissingReposInUnclassified = ($missing | Where-Object { $unclassified.Contains($_.repository_name) } | Select-Object -ExpandProperty repository_name -Unique).Count
}

$localSummary | Format-List

$token = $env:GITHUB_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) {
    throw "GITHUB_TOKEN is required for live GitHub validation."
}

$verifiedMatching = 0
$failedSize = 0
$failedCommitCount = 0
$inaccessible = 0
$presentOnDefaultBranch = 0
$historicalOnly = 0

foreach ($row in $missing) {
    $parts = Get-GitHubBlobParts $row.file_url
    try {
        $repository = Invoke-GitHubJson -Uri "https://api.github.com/repos/$($parts.Repository)" -Token $token
        $content = Test-GitHubContentExists -Repository $parts.Repository -Path $parts.Path -Ref $parts.Ref -Token $token
        if ($null -eq $content) {
            $inaccessible++
            continue
        }

        $sizeOk = [int64]$content.size -ge $MinimumSize
        $commitsChecked = Count-GitHubPathCommitsAtLeast `
            -Repository $parts.Repository `
            -Path $parts.Path `
            -Minimum $MinimumCommits `
            -Token $token
        $commitsOk = $commitsChecked -ge $MinimumCommits

        if (-not $sizeOk) {
            $failedSize++
        }
        if (-not $commitsOk) {
            $failedCommitCount++
        }
        if ($sizeOk -and $commitsOk) {
            $verifiedMatching++
        }

        $defaultContent = Test-GitHubContentExists `
            -Repository $parts.Repository `
            -Path $parts.Path `
            -Ref $repository.default_branch `
            -Token $token
        if ($null -eq $defaultContent) {
            $historicalOnly++
        } else {
            $presentOnDefaultBranch++
        }
    } catch {
        Write-Warning "Could not verify $($row.file_url): $($_.Exception.Message)"
        $inaccessible++
    }
}

[pscustomobject]@{
    VerifiedMatching = $verifiedMatching
    FailedSize = $failedSize
    FailedCommitCount = $failedCommitCount
    InaccessibleOrNotFound = $inaccessible
    PresentOnDefaultBranch = $presentOnDefaultBranch
    HistoricalOnly = $historicalOnly
} | Format-List
