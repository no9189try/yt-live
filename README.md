# YT RTMP Streamer — Step 2 (HDMI Capture Card + Logo Overlay + Audio Gain/Noise Reduction)

Step 1 के base पर अब ये नए features जुड़ गए हैं:

1. **External HDMI Capture Card (UVC) support** — Built-in camera और USB HDMI capture card के
   बीच switch कर सकते हैं (जैसे vMix/Wirecast में external input source होता है)
2. **Logo / Image Overlay** — गैलरी से logo चुनें, 5 preset positions (corners + center) में लगाएं
3. **Audio Gain** — 0% से 400% तक slider से mic volume boost/cut कर सकते हैं
4. **Noise Reduction + Echo Cancellation** — दोनों को on/off toggle कर सकते हैं

## ⚠️ बहुत ज़रूरी — पढ़ें पहले

यह code **RootEncoder** library (2.8.1) के `GenericStream` + `extra-sources` module पर based है,
जिसमें library ने खुद ही UVC capture card support (`CameraUvcSource`) built-in दे रखा है — इसलिए
हमें अलग से कोई UVC library integrate नहीं करनी पड़ी। लेकिन दो बातें ध्यान रखें:

- **`CameraUvcSource` और `AudioSource` abstract class की exact API** library के हर छोटे version
  update में थोड़ी बदलती रहती है (यह library अभी भी active development में है)। मैंने code
  latest documented API के हिसाब से लिखा है, लेकिन अगर Android Studio में build करते वक्त
  `GainMicrophoneSource.kt` या `CameraUvcSource` पर कोई "does not override" जैसा error आए —
  तो घबराएं नहीं:
  1. उस class के नाम पर **Ctrl+Click / Cmd+Click** करें (Android Studio आपको library के अंदर
     actual definition दिखा देगा)
  2. वहां दिए गए method names हमारी file में match कर लें (logic same रहेगा, सिर्फ method
     signature थोड़ा adjust करना होगा)
- यह एक genuinely complex hardware-integration feature है — बिना असली capture card और असली
  device पर test किए 100% guarantee नहीं दी जा सकती। पहले Step 1 वाला built-in camera flow अच्छे
  से test कर लें, फिर capture card जोड़कर टेस्ट करें।

## Setup

1. Android Studio में project खोलें, Gradle sync करें (अब `extra-sources` भी download होगा)।
2. Real phone पर run करें (permissions allow करें)।

## Built-in Camera से Test

- **"Built-in Camera"** button दबा रखें (default भी यही है) → preview दिखेगा → Stream Key डालकर
  **Go Live** दबाएं। यह Step 1 जैसा ही guaranteed working flow है।

## HDMI Capture Card से Test

1. एक **UVC-compliant** HDMI-to-USB capture card लें (ज़्यादातर सस्ते generic capture cards —
   जो PC पर बिना driver install किए चलते हैं — UVC-compliant होते हैं)।
2. Phone का **USB-OTG** support ज़रूरी है (ज़्यादातर modern Android phones में होता है)।
3. Capture card को OTG cable/adapter से phone में लगाएं, capture card में HDMI source (जैसे
   दूसरा camera, laptop, आदि) जोड़ें।
4. App में **"HDMI Capture Card"** button दबाएं।
5. अगर capture card detect होता है तो preview में उसका video दिखने लगेगा; permission dialog आए
   तो allow करें।
6. **नहीं दिखे तो:** capture card को PC पर टेस्ट करके पहले confirm करें कि वो चल रहा है, फिर
   powered OTG hub try करें (कुछ capture cards को ज़्यादा power चाहिए होती है जो सीधे phone से
   नहीं मिलती)।

## Logo लगाना

1. **"Logo चुनें"** दबाएं → गैलरी से PNG/JPG चुनें (transparent background वाला PNG best रहता है)
2. Logo default top-right corner में दिखेगा
3. **TL / TR / C / BL / BR** buttons से position बदलें (Top-Left, Top-Right, Center, Bottom-Left,
   Bottom-Right)
4. **"Logo हटाएं"** से हटा सकते हैं

## Audio Gain / Noise Reduction

- **Gain slider**: 100% = normal mic volume, 200% = double loud, 0% = mute। Live streaming शुरू
  करने से पहले या दौरान भी adjust कर सकते हैं।
- **Noise Reduction / Echo Cancellation switches**: हर phone का hardware अलग होता है, ये features
  हर डिवाइस पर उपलब्ध नहीं होते (कुछ सस्ते phones में यह hardware effect ही नहीं होता — तब switch
  on करने का असर नहीं दिखेगा)।

## अभी तक Cover हो चुके features
- ✅ Built-in camera + external HDMI capture card (UVC) के बीच switch
- ✅ RTMP streaming to YouTube (सिर्फ Stream Key)
- ✅ Logo/image overlay with 5 position presets
- ✅ Audio gain (0-400%)
- ✅ Noise reduction + echo cancellation toggle

## आगे क्या बचा है
- 🔲 Multi-source scene switching (Preview + Program जैसा vMix में)
- 🔲 Logo का drag-to-position (अभी सिर्फ 5 presets हैं) + resize slider
- 🔲 Bitrate/resolution settings screen
- 🔲 Local recording जो stream के साथ-साथ चले
- 🔲 Text overlay (जैसे "LIVE" badge, viewer count, आदि)

अगला feature किधर से शुरू करें, बताइए — recommend रहेगा पहले **capture card को real hardware पर
test** करना, क्योंकि उसी architecture (GenericStream) के ऊपर बाकी सब features बनेंगे।
