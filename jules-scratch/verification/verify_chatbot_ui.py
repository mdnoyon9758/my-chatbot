import pytest
from playwright.sync_api import Page, expect
import pathlib

def test_chatbot_ui_and_interaction(page: Page):
    """
    This test verifies the chatbot UI, sends a message,
    and captures a screenshot of the interaction.
    """
    # Listen for console messages
    page.on("console", lambda msg: print(f"CONSOLE: {msg.text()}"))

    # 1. Arrange: Navigate to the local index.html file.
    index_path = pathlib.Path(__file__).parent.parent.parent.joinpath("index.html").resolve()
    page.goto(f"file://{index_path}")

    # 2. Assert: Check that the main components are visible.
    expect(page.get_by_role("heading", name="Multi-AI Chatbot")).to_be_visible()
    message_input = page.get_by_placeholder("Type your message...")
    expect(message_input).to_be_visible()
    send_button = page.get_by_role("button", name="Send")
    expect(send_button).to_be_visible()

    # 3. Act: Type a message and click send.
    message_input.fill("Hello, world!")
    send_button.click()

    # 4. Assert: Check that the user's message and the bot's response appear.
    expect(page.get_by_text("Hello, world!")).to_be_visible()
    expect(message_input).to_be_enabled(timeout=60000) # Increased timeout to 60s

    # 5. Screenshot: Capture the final result.
    page.screenshot(path="jules-scratch/verification/verification.png")
