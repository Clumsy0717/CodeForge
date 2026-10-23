# CodeForge backend (OnlineCompiler.io)

This Node.js server keeps the OnlineCompiler.io API key off the Android app. The Android app calls this server, and the server calls OnlineCompiler.io.

## Setup

1. Install Node.js 20 or newer.
2. In this folder, run `npm install`.
3. Copy `.env.example` to `.env`.
4. Create a **new** REST API key in your OnlineCompiler.io dashboard and put it in `ONLINECOMPILER_API_KEY` in `.env`.
5. Check the compiler IDs in the OnlineCompiler.io dashboard/docs. If their IDs differ from the defaults in `.env`, update the four `ONLINECOMPILER_*_COMPILER` values.
6. Run `npm start`.
7. Test `GET /health`. In CodeForge, open More → Configure backend and enter the server base URL.

Do not commit `.env`, and do not put the API key in Kotlin, an APK, or GitHub. Deploy the backend behind HTTPS for real devices/public use. The Android emulator can reach a server on the host computer at `http://10.0.2.2:3000`.

## API

`POST /api/run`

```json
{
  "compiler": "python",
  "code": "print('Hello from CodeForge')",
  "input": ""
}
```

The server maps `python`, `php`, `cpp`, and `c` to the compiler IDs configured in `.env`. The response is normalized to `output`, `error`, and `status` fields. Requests are size-limited and rate-limited. Do not expose this endpoint publicly without suitable abuse protection and monitoring.

## Web projects

HTML, CSS, and browser JavaScript are previewed locally in the app's WebView; they do not use the remote execution API. Keep `index.html`, CSS, and JS files in the same project folder and link them with relative paths.
