# Smart Food Spoilage Detector

A Food freshness detection system that combines chemical colorimetric sensing with multimodal AI. The Android application captures smart labels containing natural anthocyanin indicators and leverages the Gemini 2.0 Flash API to handle complex color analysis and environment lighting correction in a single inference step.

---

## 🚀 Key Features

* **AI-Powered Visual Reasoning:** Replaced rigid, manual pixel-math thresholds with a multimodal AI model capable of understanding context, lighting, and degradation states.
* **Contextual Analysis:** The model simultaneously evaluates the color matrix of the pH indicator and the text/structural state of the packaging wrapper.
* **Edge-to-Cloud Pipeline:** Efficiently captures, compresses, and streams image frames directly to cloud API endpoints for low-latency inference.
* **Spoilage Index Output:** Parses structured AI text outputs to display a localized Freshness Index percentage on a clean native UI dashboard.

---

## 🛠️ Tech Stack & Frameworks

* **IDE:** Android Studio
* **Language:** Kotlin
* **AI Engine:** Google GenAI SDK (Gemini 2.0 Flash)
* **Networking:** OkHttp / Retrofit (API communication)
