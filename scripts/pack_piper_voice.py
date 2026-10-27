#!/usr/bin/env python3
"""
Impacchetta una voce Piper della community (.onnx + .onnx.json) nel formato di sherpa-onnx,
come le voci ufficiali: metadati nel modello, tokens.txt, espeak-ng-data. Poi la fa parlare
e misura il tono, così si sa se è maschile prima di metterla nell'app.

uso: pack_piper_voice.py <model.onnx> <config.json> <espeak-ng-data> <cartella_uscita> <nome> <licenza>
"""
import json
import shutil
import sys
import wave
from pathlib import Path

import numpy as np
import onnx


def pack(model_in, config_in, espeak, out, name, license_text):
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    config = json.loads(Path(config_in).read_text())
    model = onnx.load(model_in)
    meta = {
        "model_type": "vits",
        "comment": "piper",
        "language": config.get("language", {}).get("name_english", "Italian"),
        "voice": config["espeak"]["voice"],
        "has_espeak": 1,
        "n_speakers": config.get("num_speakers", 1),
        "sample_rate": config["audio"]["sample_rate"],
    }
    existing = {p.key for p in model.metadata_props}
    for key, value in meta.items():
        if key in existing:
            continue
        prop = model.metadata_props.add()
        prop.key, prop.value = key, str(value)
    onnx.save(model, str(out / f"{name}.onnx"))
    shutil.copy(config_in, out / f"{name}.onnx.json")
    with open(out / "tokens.txt", "w", encoding="utf-8") as f:
        for symbol, ids in config["phoneme_id_map"].items():
            f.write(f"{symbol} {ids[0]}\n")
    if not (out / "espeak-ng-data").exists():
        shutil.copytree(espeak, out / "espeak-ng-data")
    (out / "README.md").write_text(license_text + "\n", encoding="utf-8")
    return out / f"{name}.onnx"


def speak(out, onnx_path, wav_path):
    import sherpa_onnx

    cfg = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=str(onnx_path), tokens=str(out / "tokens.txt"), data_dir=str(out / "espeak-ng-data")
            ),
            num_threads=2,
        )
    )
    tts = sherpa_onnx.OfflineTts(cfg)
    audio = tts.generate(
        "Buongiorno, ecco le notizie di oggi. Il governo ha approvato la legge di bilancio, "
        "mentre a Milano continua il maltempo e in serata è attesa la pioggia."
    )
    x = np.array(audio.samples, dtype=np.float32)
    sr = audio.sample_rate
    with wave.open(str(wav_path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype("<i2").tobytes())
    f0 = []
    win = int(sr * 0.04)
    for i in range(0, len(x) - win, win // 2):
        s = x[i : i + win]
        if np.sqrt((s**2).mean()) < 0.02:
            continue
        s = s - s.mean()
        c = np.correlate(s, s, "full")[win - 1 :]
        lo, hi = int(sr / 400), int(sr / 60)
        k = lo + int(np.argmax(c[lo:hi]))
        if c[k] > 0.3 * c[0]:
            f0.append(sr / k)
    return sr, len(x) / sr, float(np.median(f0)) if f0 else 0.0


if __name__ == "__main__":
    model_in, config_in, espeak, out, name, license_text = sys.argv[1:7]
    onnx_path = pack(model_in, config_in, espeak, out, name, license_text)
    sr, seconds, f0 = speak(Path(out), onnx_path, Path(out).parent / f"{name}.wav")
    print(f"{name}: {sr} Hz, {seconds:.1f} s, tono mediano {f0:.0f} Hz")
