async function sendToChatGPT() {
    const query = document.getElementById("query").value;
    const table = document.getElementById("table").value;

    const response = await fetch("/ask", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            question: query,
            table: table
        })
    });

    const resultText = await response.text();
    renderResultAsText(resultText);
}

function renderResultAsText(text) {
    const resultBox = document.getElementById("result");

    const cleaned = text
        .split('\n')
        .filter(line => line.trim() !== '')
        .map(line => line.replace(/\bnull\b/g, '').trim())
        .join('\n');

    resultBox.innerHTML = `
        <pre style="
            white-space: pre-wrap;
            font-family: 'Segoe UI', sans-serif;
            font-size: 13px;
            line-height: 1.4;
            color: #333;
        ">${cleaned}</pre>`;
}
