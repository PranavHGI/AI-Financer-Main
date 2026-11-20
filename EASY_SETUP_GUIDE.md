wd# 🚀 Absolute Beginner's Setup Guide: AI Financer

> **Hello! Welcome to your project.**
> This guide is written step-by-step so that **anyone**, even if you have never written a line of code or used a laptop for programming before, can set up and run this project without stress. Just follow the numbered steps in order.

---

## 📋 What is Inside This Project?

This project has two main parts:
1. **The Backend (Server):** This is the "brain" that lives on the internet (or on your laptop). It talks to the database (Supabase/PostgreSQL), processes receipt photos, and handles AI advice.
2. **The Android App (Mobile):** This is the app that runs on your Android phone. It tracks your expenses, listens to voice commands like Siri, and talks to the Backend.

---

## 🗂️ STEP 1: Unzipping Your Project Folder

When you receive the `.zip` file from your friend:

1. **Do not run the project from inside the zip file.**
2. Right-click the `.zip` file (for example, `AIFinancer.zip`).
3. Click **Extract All...** (or "Extract Here").
4. Choose a simple folder on your computer. 
   * **Recommended location:** `C:\Projects\AIFinancer` or `D:\AIFinancer`.
   * *Tip:* Avoid folders with very long names or lots of parentheses.
5. Wait for the extraction bar to finish. You should now see folders named `app`, `backend`, `gradle`, etc.

---

## 🔑 STEP 2: The Google Gemini AI Key (Where to Put It)

The AI Financial Advisor and Voice Assistant use a free **Google Gemini AI Key**.

### How to get your free key (takes 2 minutes):
1. Go to [https://aistudio.google.com/app/apikey](https://aistudio.google.com/app/apikey) in your web browser.
2. Sign in with your regular Google / Gmail account.
3. Click the blue button that says **"Create API key"**.
4. Copy the long code that appears (it looks like `AIzaSy...` or similar).

### Where to put this key:
* Open the **`backend`** folder on your computer.
* Look for a file named **`.env`** (if you cannot see it, in Windows File Explorer click **View** at the top -> check the box for **Hidden items**).
* Open `.env` with **Notepad**.
* Find the line:
  ```env
  GEMINI_API_KEY=YOUR_KEY_HERE
  ```
* Paste your key there and save the file (**File -> Save**).

---

## ☁️ STEP 3: Hosting the Backend (Choose ONE of 3 Options)

You have 3 easy ways to run the backend. **Option A (Render)** is the easiest and best if you want the app to work on your phone anywhere in the world!

---

### ⭐ OPTION A: Host on Render.com (Recommended - Cloud Hosting)

Your project already has a `render.yaml` configuration file and connects to your **Supabase** cloud database!

1. Go to [https://render.com](https://render.com) and sign up / log in.
2. If your code is on GitHub:
   * Click **New +** -> **Web Service**.
   * Select your GitHub repository.
   * Render will automatically detect the `Dockerfile` inside the `backend` folder.
3. In the Render Dashboard settings for your service:
   * **Root Directory:** Type `backend`
   * **Runtime:** Select `Docker`
   * Click **Environment** (on the left menu) and make sure these variables are set:
     * `DATABASE_URL`: Your Supabase connection link (already in your `.env` file).
     * `SECRET_KEY`: Any long random text (e.g., `9a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f`).
     * `GEMINI_API_KEY`: Paste your Google Gemini API key here.
4. Click **Deploy**.
5. Once it finishes, Render will give you a public web link at the top (for example: `https://ai-financer-7a0x.onrender.com`).
6. **Save this URL!** You will paste it into the mobile app in Step 4.

---

### 🐳 OPTION B: Run on Your Laptop with Docker

If you prefer to run the backend locally on your laptop:

1. Download and install **Docker Desktop** from [docker.com](https://www.docker.com/products/docker-desktop/).
2. Open Docker Desktop and let it start up.
3. On your laptop keyboard, press the **Windows Key + R**, type `cmd`, and press **Enter**.
4. Go to your `backend` folder. Type:
   ```cmd
   cd C:\Projects\AIFinancer\backend
   ```
   *(Change the path to wherever you unzipped the project)*
5. Start the server by typing:
   ```cmd
   docker compose up --build
   ```
6. Leave this window open. The server is now running on your laptop!

---

### 🐍 OPTION C: Run Locally with Python (Without Docker)

If you don't want to use Docker:

1. Download and install **Python 3.10 or 3.11** from [python.org](https://www.python.org/). (Make sure to check the box **"Add Python to PATH"** during installation!).
2. Open Command Prompt (`cmd`) and navigate to the backend:
   ```cmd
   cd C:\Projects\AIFinancer\backend
   ```
3. Create a virtual environment:
   ```cmd
   python -m venv venv
   ```
4. Activate it:
   ```cmd
   venv\Scripts\activate
   ```
5. Install the required packages:
   ```cmd
   pip install -r requirements.txt
   ```
6. Start the server:
   ```cmd
   uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
   ```
7. You should see: `Application startup complete. Uvicorn running on http://0.0.0.0:8000`.

---

## 📱 STEP 4: Setting Up & Running the Android App

### 1. Install Android Studio
* Download and install **Android Studio** (free from [developer.android.com/studio](https://developer.android.com/studio)).
* Follow the installer instructions (click Next, Next, Finish).

### 2. Open the Project
1. Open Android Studio.
2. Click **Open** (or **File -> Open**).
3. Browse to your unzipped project folder (`AIFinancerFree`) and click **OK**.
4. Look at the bottom-right corner of Android Studio. You will see a small progress bar ("Gradle Build Model" / "Indexing"). **Wait 2 to 5 minutes** until it finishes and says "Gradle sync finished successfully".

### 3. Connect the Mobile App to Your Backend
1. On the left side of Android Studio, open this file:
   `app -> java -> com.example.aifinancerfree -> data -> network -> ApiClient.kt`
2. Look around **line 19**:
   ```kotlin
   private val BASE_URL = "https://ai-financer-7a0x.onrender.com/"
   ```
3. **If using Render (Option A):** Make sure this URL matches your Render service link (keep the trailing `/` at the end!).
4. **If running locally on your laptop (Option B or C):**
   * Find your laptop's local Wi-Fi IP address (open `cmd`, type `ipconfig`, and find `IPv4 Address`, e.g. `192.168.1.15`).
   * Change `BASE_URL` to:
     ```kotlin
     private val BASE_URL = "http://192.168.1.15:8000/"
     ```
   * *Note:* Your phone and your laptop must be on the same Wi-Fi network.

### 4. Run the App on Your Phone!
1. On your physical Android phone:
   * Go to **Settings -> About Phone**.
   * Tap **Build Number** 7 times until it says *"You are now a developer!"*.
   * Go back to **Settings -> System -> Developer Options** and turn ON **USB Debugging**.
2. Plug your phone into your laptop using a USB charging cable.
3. A popup will appear on your phone asking *"Allow USB debugging?"* -> Tap **Allow**.
4. At the top of Android Studio, look at the device dropdown next to the green Play button. You should see your phone's name (e.g. *Samsung Galaxy*, *Pixel*, *OnePlus*).
5. Click the green **Play (Run ▶)** button!
6. Android Studio will build the app and install it automatically onto your phone screen!

---

## 🎯 STEP 5: How to Test That Everything Works

Once the app opens on your phone:

1. **Register a User:**
   * Tap **Register**, type a username (e.g. `Sarthak`), email, and password. Tap **Register**.
2. **Add an Expense:**
   * Tap the **Transactions** tab -> Tap **+** (Add) -> Enter `₹250` for Food at McDonald's -> Tap **Save**.
3. **Try the Siri-like Voice Assistant:**
   * On the Home screen, tap the **Microphone button** at the bottom-right.
   * You will see the Siri-style glowing orb pulse with your voice.
   * Say: *"Spent 150 on coffee at Starbucks"*.
   * The assistant will automatically log the transaction and speak back to you!
4. **Scan Receipts:**
   * Tap **Add Transaction** -> Tap **Scan Receipt** -> Take a photo of any grocery or restaurant bill -> The app will automatically extract the total and store name!

## 🚀 STEP 6: Pushing Code Updates to GitHub & Render

When you want to save your code to GitHub or trigger an automatic update on Render:

1. Open Command Prompt (`cmd`) in your project folder:
   ```cmd
   cd C:\Projects\AIFinancer
   ```
2. Run these 3 simple git commands:
   ```cmd
   git add .
   git commit -m "Update project fixes and settings"
   git push origin main
   ```
3. **Render will automatically detect your push** and redeploy the backend within 1 to 2 minutes!

> **Note for Render:** Render only looks inside the `backend/` folder (`rootDir: backend`). Any guide files in the root folder are automatically skipped by Render during the build.

---

## 🆘 Quick Troubleshooting (Common Fixes)

* **Problem: The app says "Network Error" or won't log in:**
  * If using Render: Check if your Render web service is "Live" (green dot) in your Render dashboard.
  * If using local Wi-Fi: Make sure both phone and laptop are on the same Wi-Fi, and that Windows Firewall isn't blocking port 8000.
* **Problem: Server crashes with `tenant/user not found` or `connection to Supabase failed`:**
  * Supabase free projects automatically pause after 7 days of inactivity!
  * **Fix 1 (Cloud):** Go to [supabase.com](https://supabase.com), log in, and click **Restore project**.
  * **Fix 2 (Instant Offline Mode):** Open `backend/.env` in Notepad and change `DATABASE_URL` to:
    ```env
    DATABASE_URL=sqlite:///./aifinancer.db
    ```
    This switches the database to a local SQLite file so it works instantly on your laptop without needing Supabase or the internet!
* **Problem: Voice Assistant isn't hearing you:**
  * When the app asks for **Microphone Permission**, make sure you tap **"While using the app"**.
* **Problem: SMS auto-tracking not scanning:**
  * Go to your phone's **Settings -> Apps -> AI Financer -> Permissions** and make sure **SMS permission** is set to **Allow**.

---

*Enjoy your AI Financer project! If you ever need to change your database or server, everything is cleanly organized in `backend/.env` and `ApiClient.kt`.*
