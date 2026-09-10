# Generates only the adjacent, self-authored fictional transcript, offline.
# No microphone, personal recording, provider API, or credential is accessed.
param([ValidateSet('glg', 'spa')][string]$Language = 'glg')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$pilotSuffix = if ($Language -eq 'spa') { 'es' } else { 'gl' }
$pilotText = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $PSScriptRoot ("fixtures/hosted-pilot.$pilotSuffix.txt"))
$pilotDestination = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ("../processing-worker/.wrangler/rollout/hosted-pilot.$pilotSuffix.wav")))
$pilotVoice = New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    # This Windows installation has a Spanish voice, not a native Galician one.
    # Galician text is suitable for pipeline checks, not an accent/accuracy study.
    $pilotVoice.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::NotSet, [System.Speech.Synthesis.VoiceAge]::NotSet, 0, [System.Globalization.CultureInfo]::GetCultureInfo('es-ES'))
    $pilotVoice.SetOutputToWaveFile($pilotDestination)
    $pilotVoice.Speak($pilotText)
} finally {
    $pilotVoice.Dispose()
}
Write-Output ('Generated fictional pilot audio: ' + $pilotDestination)
