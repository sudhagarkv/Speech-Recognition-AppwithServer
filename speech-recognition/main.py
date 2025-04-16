import os
import wave
import uuid
import json
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from vosk import Model, KaldiRecognizer

app = FastAPI()


MODEL_PATH = "model"  
if not os.path.exists(MODEL_PATH):
    raise FileNotFoundError("Vosk model not found. Download and unzip it into 'model' directory.")
model = Model(MODEL_PATH)

@app.websocket("/ws/speech")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()

    session_id = str(uuid.uuid4())
    os.makedirs("recordings", exist_ok=True)
    audio_file_path = f"recordings/{session_id}.wav"

    wf = wave.open(audio_file_path, 'wb')
    wf.setnchannels(1)
    wf.setsampwidth(2)  
    wf.setframerate(16000)

    recognizer = KaldiRecognizer(model, 16000)
    print(f"[Session Started] {session_id}")

    try:
        while True:
            data = await websocket.receive_bytes()
            wf.writeframes(data)

            if recognizer.AcceptWaveform(data):
                result = json.loads(recognizer.Result())
                await websocket.send_text(json.dumps({
                    "type": "final",
                    "text": result.get("text", "")
                }))
            else:
                partial = json.loads(recognizer.PartialResult())
                await websocket.send_text(json.dumps({
                    "type": "partial",
                    "text": partial.get("partial", "")
                }))
    except WebSocketDisconnect:
        wf.close()
        print(f"[Session Ended] {session_id}")
        await websocket.close()
    except Exception as e:
        wf.close()
        print(f"[ERROR] {str(e)}")
        await websocket.close()
