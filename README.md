# CodeForge

CodeForge is an Android code editor starter project built with Kotlin, XML layouts, and Gradle.

## Features

- Create and edit project files, with local saving.
- Open `.html` / `.htm` files in an external browser app; large projects should be served over HTTP so linked assets load reliably.
- Run Python, PHP, C, and C++ through the companion Node.js backend and OnlineCompiler.io.
- The API key stays on the backend, never in the Android app.

## Run the Android app

Open the `CodeForge` folder in Android Studio and allow Gradle sync. Build and run on an emulator or device.

## Connect the online runner

Start the companion server in `../CodeForge-Backend` following its README, then use **More → Settings** to configure editor preferences and the backend URL, then use **Test connection** to check `/health`. For the Android emulator, use `http://10.0.2.2:3000` when the backend runs on your development computer. For a real device or deployment, use a reachable HTTPS backend URL.

The compiler identifiers in the backend `.env` must match the current OnlineCompiler.io compiler IDs. API availability and usage limits depend on your account.

## Important

- Do not add a real API key to this repository or Android app.
- If a key was pasted into chat or committed to a repository, revoke it and create a replacement.
- Web preview runs browser JavaScript locally. It is not a full browser and does not provide a PHP server or run server-side PHP inside the preview.

## Settings, welcome screen, and HTML browser behavior

The app now starts with a welcome menu for New File, Open Folder, Open File, and New Project, and no longer creates sample files automatically. Use the Explorer panel to see files in the current CodeForge project.

Only `.html` / `.htm` files launch an external browser when you tap Run. CSS and JS files are support files; open `index.html` to view the page. Important: Android browsers may not load relative assets from an app-private `content://` file. For multi-file or large websites, run the project from a local/deployed HTTP server to ensure CSS, JS, images, and other files load correctly.

The Open File action imports the selected file into the current CodeForge project. The folder picker requests folder access, but full SAF folder browsing is not implemented yet.

## Build compatibility note

This project uses Android Gradle Plugin 8.7.3 with Gradle 8.9. Gradle distribution downloads require an internet connection the first time you build.
