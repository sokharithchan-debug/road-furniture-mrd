# កម្មវិធីស្រង់ទិន្នន័យបរិក្ខារចរាចរណ៍

**ក្រសួងអភិវឌ្ឍន៍ជនបទ — នាយកដ្ឋានអភិវឌ្ឍន៍ហេដ្ឋារចនាសម្ព័ន្ធផ្លូវជនបទ**

Traffic sign and guardrail inventory, collected by chainage in the field.
Khmer interface, works with no signal, and exports the ministry forms from the
phone itself — Excel (ទម្រង់ទី ១ and ទម្រង់ទី ៣.១/៣.២), CSV, and a printable PDF.

Everything stays on the phone. There is no server and no account.

## How the chainage works

The chainage is a position along the line the car has actually driven, measured
from the starting PK. New ground stretches that line and the chainage grows with
it. Driving back over a stretch already covered slides the position back down the
same line, so the chainage falls — and that is the only way it falls. Leaving the
line never subtracts anything; it starts a new stretch from where the car is.

## What is in here

| Path | What it is |
|---|---|
| `index.html` | The whole app — HTML, CSS, JS, Khmer font, sign images and the Excel templates, in one file |
| `manifest.webmanifest` | Name, icons and colours when the app is installed from the browser |
| `sw.js` | Service worker — keeps the web version opening with no signal |
| `icons/` | Home-screen icons |
| `.nojekyll` | Tells GitHub Pages to serve the folder as it is |
| `android/` | The Android project that wraps `index.html` into an APK |
| `.github/workflows/android.yml` | Builds the APK on GitHub |

## The web version (GitHub Pages)

**Settings → Pages → Source: Deploy from a branch**, branch `main`, folder
`/ (root)`. The address will be `https://<username>.github.io/<repo>/`.
HTTPS is required for GPS, and Pages provides it.

To install from the browser: Android/Chrome → menu → *Add to Home screen*.
iPhone/Safari → Share → *Add to Home Screen*. On iPhone, run it from the icon
rather than a Safari tab, or iOS will not keep the survey across reloads.

## The Android app (APK)

GitHub builds it — nothing to install on your computer.

1. **Actions** tab → **Build APK** → **Run workflow** (or just push a change to
   `index.html`, which starts a build on its own).
2. When the run finishes, open it, scroll to **Artifacts**, and download
   **road-furniture-mrd-apk**. Inside is `road-furniture-mrd-1.<n>.apk`.
3. Copy the file to the phone and open it. Android will ask permission to install
   from this source the first time.

The APK holds a copy of `index.html`, so it needs no address and no signal at all.

After installing, in **Settings → Apps → បរិក្ខារចរាចរណ៍**:

- Location → **Allow all the time**, and **Precise**
- Camera → allow
- Battery → turn optimisation **off**, or Android throttles the GPS on a long run

Later builds install over the old one and keep the saved survey.

## Updating

Replace `index.html` and commit. That rebuilds the APK by itself. For the web
version, also bump the cache name at the top of `sw.js` (`...-v5` → `-v6`),
otherwise phones that already installed it keep serving the old build.

## The signing key

`android/keystore.jks` is in this repository, password `mrdroad`. It exists so
every build installs over the last one on the same phone. That is fine for
handing APKs around the department; it is **not** safe for Google Play, because
anyone with the repository has the key. For Play, generate a private key, keep it
out of the repository, and put it in GitHub Secrets.

## Notes

- Sign codes follow the national standard (`PW03-<category><n>-<nn>`).
- Photos are not kept across reloads; the route, the entries and the guardrail
  runs are.
- Exports land in the phone's **Downloads** folder.
