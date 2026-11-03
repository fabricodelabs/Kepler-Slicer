param([switch]$Stream,[switch]$ShowConfiguration)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if((& git branch --show-current) -ne 'claude/ui'){throw 'Run only from Kepler-Claude on claude/ui'}
$budget='1.00'; $turns='12'; $model='sonnet'; $effort='medium'
if($ShowConfiguration){
    [ordered]@{model=$model;effort=$effort;maxBudgetUsd=$budget;maxTurns=$turns;mode='print';autoRestart=$false} | ConvertTo-Json
    exit 0
}
$command=Get-Command claude -ErrorAction SilentlyContinue
if($command){$cli=$command.Source}else{
    $package=Join-Path $env:LOCALAPPDATA 'Microsoft/WinGet/Packages/Anthropic.ClaudeCode_Microsoft.Winget.Source_8wekyb3d8bbwe'
    $cli=(Get-ChildItem -LiteralPath $package -Filter claude.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
}
if(!$cli){throw 'Claude Code not installed'}
& $cli auth status *> $null
if($LASTEXITCODE -ne 0){throw 'Claude is not signed in. Run claude auth login and complete browser sign-in, then rerun this launcher.'}
$prompt=Get-Content -LiteralPath (Join-Path $projectRoot 'docs/collaboration/CLAUDE-TASK.md') -Raw
$arguments=@('-p','--model',$model,'--effort',$effort,'--max-budget-usd',$budget,'--max-turns',$turns,
    '--permission-mode','acceptEdits','--tools','Read,Glob,Grep,Edit,Write','--disallowedTools','Bash,PowerShell,mcp__*')
if($Stream){$arguments+=@('--output-format','stream-json','--verbose')}
else{$arguments+=@('--output-format','json')}
& $cli @arguments '--' $prompt
if($LASTEXITCODE -ne 0){throw "Claude exited with code $LASTEXITCODE"}
