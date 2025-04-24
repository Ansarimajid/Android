from flask import Flask, request, jsonify
import os

app = Flask(__name__)

UPLOAD_FOLDER = 'uploads'
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

@app.route('/', methods=['POST'])
def receive_data():
    if 'image' not in request.files or 'text' not in request.form:
        return jsonify({"error": "Missing image or text"}), 400

    image = request.files['image']
    text = request.form['text']

    # Save the image
    image_path = os.path.join(UPLOAD_FOLDER, image.filename)
    image.save(image_path)

    # Log or process text
    print("Received text:", text)
    print("Image saved to:", image_path)

    return jsonify({"message": "Text and image received successfully"}), 200

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=3000)
