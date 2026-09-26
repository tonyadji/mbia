#!/usr/bin/env python3
"""Generates the committed image fixtures of the media tests (Phase 3 plan §3.8, ADR-007).

Every file stays under 100 KB. The images carrying metadata hold EXIF GPS and XMP, so that the
tests can prove the served derivatives hold none.

Usage (from the repository root, once):

    python3 -m venv .venv-fixtures
    .venv-fixtures/bin/pip install Pillow==12.3.0
    .venv-fixtures/bin/python backend/scripts/generate-media-fixtures.py
"""

import io
import struct
import zlib
from pathlib import Path

from PIL import Image, ImageDraw
from PIL.PngImagePlugin import PngInfo

OUT = Path(__file__).resolve().parent.parent / "src" / "test" / "resources" / "media"
MAX_BYTES = 100 * 1024

XMP = (b'<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">'
       b'<rdf:Description xmlns:exif="http://ns.adobe.com/exif/1.0/" exif:GPSLatitude="4.0511N" '
       b'exif:GPSLongitude="9.7679E"/></rdf:RDF></x:xmpmeta>')

RED = (220, 20, 20)
BLUE = (20, 20, 220)


def exif(orientation=None):
    """EXIF with a GPS position (Douala), and an orientation when given."""
    data = Image.Exif()
    if orientation is not None:
        data[0x0112] = orientation
    gps = data.get_ifd(0x8825)
    gps[1] = "N"
    gps[2] = (4.0, 3.0, 4.0)
    gps[3] = "E"
    gps[4] = (9.0, 46.0, 4.0)
    return data


def quadrant_image(width, height):
    """Blue, with the top-left quadrant red: shows where the top-left corner ends up."""
    image = Image.new("RGB", (width, height), BLUE)
    ImageDraw.Draw(image).rectangle((0, 0, width // 2 - 1, height // 2 - 1), fill=RED)
    return image


def save(name, data):
    assert len(data) <= MAX_BYTES, f"{name} is {len(data)} bytes"
    (OUT / name).write_bytes(data)
    print(f"{name}: {len(data)} bytes")


def encode(image, fmt, **options):
    buffer = io.BytesIO()
    image.save(buffer, fmt, **options)
    return buffer.getvalue()


def png_with_metadata(image):
    """XMP in an iTXt chunk, and EXIF in an eXIf chunk (raw TIFF, without the JPEG "Exif" prefix)
    inserted after IHDR by hand: Pillow does not write it."""
    info = PngInfo()
    info.add_itxt("XML:com.adobe.xmp", XMP.decode())
    data = encode(image, "PNG", pnginfo=info)
    after_ihdr = 8 + 12 + 13
    return data[:after_ihdr] + png_chunk(b"eXIf", exif().tobytes()[6:]) + data[after_ihdr:]


def png_chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


def main():
    OUT.mkdir(parents=True, exist_ok=True)

    # Stored 64×32 landscape, displayed 32×64 portrait: orientation 6 turns it 90° clockwise.
    save("exif-gps-orientation-6.jpg",
         encode(quadrant_image(64, 32), "JPEG", quality=95, exif=exif(6), xmp=XMP))
    save("gps.png", png_with_metadata(quadrant_image(64, 32)))
    save("gps.webp", encode(quadrant_image(64, 32), "WEBP", quality=90, exif=exif(), xmp=XMP))

    save("text-renamed.jpg", b"This is not an image, only text renamed with a .jpg extension.\n")
    save("jpeg-declared-as-png.png", encode(quadrant_image(64, 32), "JPEG", quality=90))

    # A valid PNG header announcing 8000×6000 (48 megapixels), with a truncated image stream: it
    # must be refused from its header, before anything is decoded.
    ihdr = struct.pack(">IIBBBBB", 8000, 6000, 8, 2, 0, 0, 0)
    idat = zlib.compress(b"\x00" * 1024)
    save("over-40-megapixels.png",
         b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", ihdr) + png_chunk(b"IDAT", idat) + png_chunk(b"IEND", b""))

    # A phone-sized photo above the 2560 px of the browser downscale, with GPS (E2E of PR-38, and
    # the 2048 / 480 px bounds of the derivatives).
    save("large-with-gps-3000x2000.jpg",
         encode(quadrant_image(3000, 2000), "JPEG", quality=50, exif=exif(), xmp=XMP))


if __name__ == "__main__":
    main()
