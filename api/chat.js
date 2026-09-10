export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");

  if (req.method === "OPTIONS") {
    return res.status(200).end();
  }

  if (req.method !== "POST") {
    return res.status(405).json({
      error: "Method not allowed"
    });
  }

  try {
    const apiKey = process.env.DEEPSEEK_API_KEY;

    if (!apiKey) {
      console.error("DEEPSEEK_API_KEY is missing");

      return res.status(500).json({
        error: "DEEPSEEK_API_KEY is not configured"
      });
    }

    const { message } = req.body || {};

    if (!message || typeof message !== "string") {
      return res.status(400).json({
        error: "Missing message"
      });
    }

    console.log("ATLAS request:", message);

    const response = await fetch(
      "https://api.deepseek.com/responses",
      {
        method: "POST",

        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${apiKey}`
        },

        body: JSON.stringify({
          model: "deepseek-v4-flash",

          instructions:
            "You are ATLAS, a conversational AI assistant. " +
            "Be natural, helpful, concise, intelligent, and friendly. " +
            "Answer the user's request directly. " +
            "Do not claim to have performed actions that you did not perform.",

          input: message,

          temperature: 0.7,

          max_output_tokens: 1000
        })
      }
    );

    const data = await response.json();

    console.log("DeepSeek status:", response.status);

    if (!response.ok) {
      console.error("DeepSeek error:", data);

      return res.status(response.status).json({
        error:
          data?.error?.message ||
          data?.message ||
          "DeepSeek API request failed"
      });
    }

    let reply = data.output_text || "";

    if (!reply && Array.isArray(data.output)) {
      for (const item of data.output) {
        if (
          item?.type === "message" &&
          Array.isArray(item.content)
        ) {
          for (const content of item.content) {
            if (
              content?.type === "output_text" &&
              typeof content.text === "string"
            ) {
              reply += content.text;
            }
          }
        }
      }
    }

    if (!reply.trim()) {
      console.error("DeepSeek returned no text:", data);

      return res.status(500).json({
        error: "DeepSeek returned an empty response"
      });
    }

    console.log("ATLAS reply:", reply);

    return res.status(200).json({
      reply: reply.trim()
    });

  } catch (error) {
    console.error("ATLAS server error:", error);

    return res.status(500).json({
      error:
        error?.message ||
        "Server error while contacting DeepSeek"
    });
  }
}