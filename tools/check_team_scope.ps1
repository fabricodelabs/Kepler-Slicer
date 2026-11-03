param([ValidateSet('Auto','Claude','Codex')][string]$Role='Auto',[switch]$Staged,[string[]]$Paths)
$ErrorActionPreference='Stop'
$root = (& git rev-parse --show-toplevel)
if($LASTEXITCODE -ne 0){throw 'Not a Git repository'}
$branch = (& git branch --show-current)
if($Role -eq 'Auto'){
    if($branch -like 'claude/*'){$Role='Claude'}
    elseif($branch -like 'codex/*'){$Role='Codex'}
    else{Write-Output "Integration branch ${branch}: scope review belongs to integrator"; exit 0}
}
$policy=Get-Content -LiteralPath (Join-Path $root 'docs/collaboration/ownership.json') -Raw | ConvertFrom-Json
if(!$Paths){
    if($Staged){$Paths=@(& git -c core.quotepath=false diff --cached --name-only --diff-filter=ACDMRTUXB)}
    else{$Paths=@(& git -c core.quotepath=false diff HEAD --name-only)+@(& git -c core.quotepath=false ls-files --others --exclude-standard)}
    if($LASTEXITCODE -ne 0){throw 'Cannot enumerate changes'}
}
$violations=@(foreach($path in @($Paths | Sort-Object -Unique)){
    $owned=$false
    foreach($pattern in $policy.claude){if($path -like $pattern){$owned=$true;break}}
    if(($Role -eq 'Claude' -and !$owned) -or ($Role -eq 'Codex' -and $owned)){$path}
})
if($violations.Count){$violations | ForEach-Object {Write-Output "Out of $Role scope: $_"}; exit 1}
Write-Output "PASS: $Role scope ($(@($Paths).Count) paths)"
