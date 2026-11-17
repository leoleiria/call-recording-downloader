# User guide

## 1. Create a Ringover API key

1. Sign in to the Ringover dashboard.
2. Go to **Developer → API keys** and click **Create an API key**.
3. Choose the user and give the key a label, for example "Call Recording Downloader".
4. Give it **Calls → Read** and nothing else. A key with fewer permissions does less harm if it
   ever leaks.
5. Copy the key. Keep it somewhere safe, such as a password manager.

Ringover hosts accounts in **Europe** or the **United States**. Use the region of your account in the
app; a key does not work against the other region.

## 2. Install and first launch

**Windows**: run `call-recording-downloader-<version>-windows-x64.msi`. It installs for your user
only (no administrator rights), adds the app to the Start menu and can add a desktop shortcut.
Installing a newer `.msi` upgrades the existing installation; uninstall it from **Settings → Apps**.
If you cannot install software, use the portable `.zip`: unzip it anywhere and run
`Call Recording Downloader.exe`.

Windows may show "Windows protected your PC" because the app is not code-signed yet. Click **More
info → Run anyway**. On macOS, right-click the app, choose **Open**, then confirm.

The first time, the app asks for your language. You can change it later with the **Language**
button.

## 3. Search the calls

1. Choose the **Region** of your Ringover account.
2. Type the **Contact number** in international format with the country code, for example
   `+34666666666`. Spaces and dashes are ignored.
3. Type or paste the **API key**. It is masked; click **Show** to check what you pasted.
4. Pick the **Period**, up to one year. Ringover only accepts a few days per request, so the app
   searches long periods in 14-day steps automatically. A full year takes about 15 seconds.
5. Click **Search calls**.

The app shows how many calls were found, how many recordings (voicemails included) and how many
transcriptions are available.

## 4. Download

Click one of:

- **Download recordings and transcriptions**
- **Download recordings only** (voicemails included)
- **Download transcriptions only**

Choose a folder. The app creates a sub-folder named after the contact number (without `+`) and saves
the files there. When it finishes, click **Open folder**.

You can run the same download again at any time: files already saved are skipped, so only what is
missing is downloaded. **Cancel** stops after the current file; nothing half-written is left behind.

### Files

| File | Content |
|---|---|
| `…_in_<callId>.mp3` / `…_out_<callId>.mp3` | Call recording (`in` = received, `out` = placed) |
| `…_voicemail.mp3` | Voicemail left by the contact |
| `…_transcription.txt` | Readable transcription: `[mm:ss] Caller: …` |
| `OriginalTranscription_Json/…_transcription.json` | The transcription exactly as Ringover returned it |
| `OriginalTranscription_Json/manifest.csv` | One row per call: id, start time (UTC), direction, duration and files (paths relative to the contact folder) |

## 5. Privacy and security

- The API key is kept in memory only while the app is open. It is **never** written to disk, and it
  is masked in every log line.
- The app remembers only your language, region and last folder.
- Downloaded recordings contain personal data. Store them according to your company's policies and
  the applicable law (for example the GDPR).

## 6. Troubleshooting

| Message | What to do |
|---|---|
| Ringover rejected the API key | Check the key, the region and that the key has **Calls → Read**. |
| Transcriptions are not included in your plan | Transcriptions require the feature in your Ringover plan. Recordings still download. |
| Too many requests | Ringover allows 2 requests per second. The app already waits and retries; try again in a minute. |
| Could not write the files | Choose a folder you can write to and check the free disk space. |

Log files are in `~/.call-recording-downloader/logs/` (on Windows
`C:\Users\<you>\.call-recording-downloader\logs\`). They never contain your API key, so you can
attach them to a [bug report](https://github.com/leoleiria/call-recording-downloader/issues). They
can contain call ids, so check them before sharing publicly.
