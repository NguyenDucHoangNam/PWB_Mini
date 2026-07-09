import { ImageResponse } from "next/og";
import fs from "fs";
import path from "path";

export const size = {
  width: 32,
  height: 32,
};
export const contentType = "image/png";

export default function Icon() {
  const imagePath = path.join(process.cwd(), "src/app/icon-source.png");
  const imageBuffer = fs.readFileSync(imagePath);
  const base64Image = imageBuffer.toString("base64");
  const dataUrl = `data:image/png;base64,${base64Image}`;

  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          borderRadius: "50%",
          overflow: "hidden",
        }}
      >
        <img
          src={dataUrl}
          width="100%"
          height="100%"
          style={{
            objectFit: "cover",
            borderRadius: "50%",
          }}
        />
      </div>
    ),
    {
      ...size,
    }
  );
}
