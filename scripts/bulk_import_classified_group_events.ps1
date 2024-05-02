param (
    [switch]$Help,
    [string]$BeginTime = "1970-01-01",
    [string]$EndTime = "2100-01-01",
    [string]$Env = "dev",
    [string]$SystemList,
    [switch]$DebugMode
)

function Usage {
@"
Usage: $script:MyInvocation.MyCommand [-Help] [-BeginTime <begin_time>] [-EndTime <end_time>] [-Server <server[:port]] [-DebugMode]
-Help              Show this help message
-BeginTime         Earliest event time to import (default: 1970-01-01)
-EndTime           Latest event time to import (default: 2100-01-01)
-Env               Configuration environment (default: 'dev')
-SystemList        Comma separated list of systems to add
-DebugMode         Enable debug mode
"@
}

function Get-Config {
param (
    [string] $Filename = "./configs.json",
    [string] $Env = "dev"
)
    $out = Get-Content $filename | ConvertFrom-Json
    $config = $out.$env
    return $config
}

function Do-KeycloakAuth {
param (
    [string] $url,
    [string] $curl_config
)

    # curl_config needs to contain -u<username>:<secret> for an account with wfb_eventpost role (probably wfbadm)
    $result = curl.exe -s -d "grant_type=client_credentials" -K ${curl_config} ${url} 2>&1
    $token = (ConvertFrom-Json $result).access_token
    return $token
}

function Get-UnixTime {
param (
    [string] $DateTime
)
    return Get-Date -Date "$DateTime" -UFormat %s
}


# Process Options
if ($Help) {
    Usage
    exit 0
}

$config = Get-Config -Env $Env
$EVENT_URL =  $config.event_url
$AUTH_URL = $config.auth_url
$CURL_CONFIG = "..\cfg\add_event1.0.cfg"

$BEGIN = Get-UnixTime -DateTime $BeginTime
$END = Get-UnixTime -DateTime $EndTime
$AUTH = 0
$GROUPED = $true
$data_dir = "../data"
$system_list = $SystemList.Split(',')

$access_token = Do-KeycloakAuth -url $AUTH_URL -curl_config $CURL_CONFIG

foreach ($system in $system_list) {
    foreach ($location in Get-ChildItem "$data_dir\$system" | Where-Object { $_.PSIsContainer }) {
        foreach ($classification in Get-ChildItem "$data_dir\$system\$location" | Where-Object { $_.PSIsContainer }) {
            foreach ($date in Get-ChildItem "$data_dir\$system\$location\$classification" | Where-Object { $_.PSIsContainer }) {
                $date_folder = "$data_dir\$system\$location\$classification\$date"
                if ($date.Name -eq "older") {
                    Write-Host "Skipping folder $date_folder"
                    continue
                }

                $datef = $date.Name -replace '_', '-'
                $event_date = Get-UnixTime -DateTime "$datef"

                if ($BEGIN -gt $event_date -or $END -lt $event_date) {
                    Write-Host "Skipping event folder $date_folder"
                    continue
                }

                # These can be folder or tar.gz'ed folders (so files).  Don't filter on only folders like the other
                # loops
                foreach ($time in Get-ChildItem "$date_folder") {

                    # Convert to a usable time format and strip off '.tar.gz' if it's a tgz'ed file and not a directory
                    $match = $time.name | Select-String -Pattern '(\d\d)(\d\d)(\d\d)(.\d).*$'
                    $timef = $match.Matches.groups[1].value + ":" + $match.Matches.groups[2].value + ":" +
                             $match.Matches.groups[3].value + "" + $match.Matches.groups[4].value

                    # Filter out events that are not in our time range
                    $event_time = Get-UnixTime -DateTime "$datef $timef"

                    if ($BEGIN -lt $event_time -and $END -gt $event_time) {
                        Write-Host "POSTing $date_folder/$time"

                        curl.exe -H "Authorization: bearer $access_token" -d datetime="$datef $timef" -d location="$location" -d system="$system" -d classification="$classification" -d grouped="$GROUPED" $EVENT_URL

                        Write-Host
                    } else {
                        Write-Host "Skipping event $system/$location/$date/$time"
                    }
                }
            }
        }
    }
}
