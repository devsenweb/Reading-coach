"""Records every phrase the app speaks with a natural voice (Kokoro TTS).

The page plays these clips instead of the phone's robotic text-to-speech, and falls back to
text-to-speech for anything not recorded (or when offline). Run after changing any spoken line:

    pip install kokoro-onnx soundfile   # plus ffmpeg
    # model files: https://github.com/thewh1teagle/kokoro-onnx/releases/tag/model-files-v1.0
    python3 tools/make_voice.py --model kokoro-v1.0.int8.onnx --voices voices-v1.0.bin
"""
import argparse, hashlib, json, os, re, subprocess, tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PAGE = os.path.join(ROOT, "app/src/main/assets/index.html")
OUT = os.path.join(ROOT, "app/src/main/assets/voice")
VOICE, SPEED = "af_heart", 0.95


def phrases():
    html = open(PAGE, encoding="utf-8").read()
    cheers = html[html.index("// ---------- cheers"):html.index("// ---------- session")]
    # every sentence-like string in the cheers code is something the app can say
    found = {s for s in re.findall(r'"([A-Z0-9][^"\\(){}+=<>]*[!.?])"', cheers)}
    for total in (6, 10, 12):  # end-of-round lines with a score
        for right in range(1, total + 1):
            found.add(f"Great job! You got {right} out of {total}!")
            found.add(f"Good work! {right} out of {total}. Keep practicing!")
    return sorted(found)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--voices", required=True)
    a = ap.parse_args()
    from kokoro_onnx import Kokoro
    import soundfile as sf

    k = Kokoro(a.model, a.voices)
    os.makedirs(OUT, exist_ok=True)
    clips = {}
    for text in phrases():
        name = hashlib.sha1(f"{VOICE}|{SPEED}|{text}".encode()).hexdigest()[:10] + ".mp3"
        clips[text] = name
        path = os.path.join(OUT, name)
        if os.path.exists(path):
            continue
        samples, rate = k.create(text, voice=VOICE, speed=SPEED, lang="en-us")
        with tempfile.NamedTemporaryFile(suffix=".wav") as wav:
            sf.write(wav.name, samples, rate)
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav.name, "-ac", "1", "-b:a", "48k", path], check=True)
    for f in os.listdir(OUT):  # drop clips for lines that changed
        if f.endswith(".mp3") and f not in clips.values():
            os.remove(os.path.join(OUT, f))
    with open(os.path.join(OUT, "index.json"), "w", encoding="utf-8") as f:
        json.dump({"voice": VOICE, "clips": clips}, f, ensure_ascii=False, indent=0)
    print(f"{len(clips)} clips in {OUT}")


if __name__ == "__main__":
    main()
