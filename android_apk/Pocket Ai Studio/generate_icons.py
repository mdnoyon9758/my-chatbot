#!/usr/bin/env python3
"""Generate placeholder launcher icons for Pocket AI Studio."""
from PIL import Image, ImageDraw, ImageFont
import os

def create_icon(size, output_path):
    # Create a new image with a blue background
    img = Image.new('RGBA', (size, size), (33, 150, 243, 255))  # Material Blue 500
    draw = ImageDraw.Draw(img)
    
    # Draw a white circle
    margin = size // 8
    circle_bbox = [margin, margin, size - margin, size - margin]
    draw.ellipse(circle_bbox, fill=(255, 255, 255, 255))
    
    # Draw "AI" text
    try:
        # Try to use a monospace font
        font_size = size // 3
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", font_size)
    except (OSError, IOError):
        # Fallback to default font
        font = ImageFont.load_default()
    
    text = "AI"
    # Get text bounding box
    text_bbox = draw.textbbox((0, 0), text, font=font)
    text_width = text_bbox[2] - text_bbox[0]
    text_height = text_bbox[3] - text_bbox[1]
    
    # Center the text
    x = (size - text_width) // 2
    y = (size - text_height) // 2
    
    # Draw text in blue
    draw.text((x, y), text, fill=(33, 150, 243, 255), font=font)
    
    # Save the image
    img.save(output_path, 'PNG')
    print(f"Created {output_path} ({size}x{size})")

def main():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    res_dir = os.path.join(base_dir, "app", "src", "main", "res")
    
    # Icon sizes for each density
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    
    for density, size in densities.items():
        density_dir = os.path.join(res_dir, density)
        os.makedirs(density_dir, exist_ok=True)
        
        # Regular icon
        ic_launcher_path = os.path.join(density_dir, "ic_launcher.png")
        create_icon(size, ic_launcher_path)
        
        # Round icon (same for now)
        ic_launcher_round_path = os.path.join(density_dir, "ic_launcher_round.png")
        create_icon(size, ic_launcher_round_path)

if __name__ == "__main__":
    main()
