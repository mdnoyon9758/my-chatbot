document.addEventListener('DOMContentLoaded', () => {
    const messageContainer = document.getElementById('message-container');
    const messageInput = document.getElementById('message-input');
    const sendButton = document.getElementById('send-button');
    const chatWindow = document.getElementById('chat-window');
    const modelSelector = document.getElementById('model-selector');

    sendButton.addEventListener('click', sendMessage);
    messageInput.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            sendMessage();
        }
    });

    function sendMessage() {
        const messageText = messageInput.value.trim();
        if (messageText === '' || messageInput.disabled) {
            return;
        }

        setProcessingState(true);
        displayMessage(messageText, 'user');
        messageInput.value = '';

        const thinkingIndicator = displayMessage('Thinking...', 'bot', true);

        const selectedModel = modelSelector.value;
        const options = {};
        if (selectedModel !== 'default') {
            options.model = selectedModel;
        }

        puter.ai.chat(messageText, options)
            .then(response => {
                messageContainer.removeChild(thinkingIndicator);
                displayMessage(response, 'bot');
            })
            .catch(error => {
                messageContainer.removeChild(thinkingIndicator);
                console.error('Error calling Puter.ai:', error);
                displayMessage('Sorry, something went wrong. Please check the console for details.', 'bot');
            })
            .finally(() => {
                setProcessingState(false);
            });
    }

    function displayMessage(text, sender, isThinking = false) {
        const messageElement = document.createElement('div');
        messageElement.classList.add('message', sender === 'user' ? 'user-message' : 'bot-message');
        if (isThinking) {
            messageElement.classList.add('thinking');
        }
        messageElement.textContent = text;
        messageContainer.appendChild(messageElement);
        chatWindow.scrollTop = chatWindow.scrollHeight;
        return messageElement;
    }

    function setProcessingState(isProcessing) {
        messageInput.disabled = isProcessing;
        sendButton.disabled = isProcessing;
        if (!isProcessing) {
            messageInput.focus();
        }
    }
});
