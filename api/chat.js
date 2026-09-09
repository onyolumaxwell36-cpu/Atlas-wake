export default async function handler(req, res) {
  // Allow ATLAS to call this endpoint.
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");

  // Handle browser/preflight requests.
  if (req.method === "OPTIONS") {
    return res.status(200).end();
  }

  // Only POST is allowed.
  if (req.method !== "POST") {
    return res.status(405).json({
      error: "Method not allowed"
    });
  }

  try {
    // Get the secret key from Vercel environment variables.
    const apiKey = process.env.OPENAI_API_KEY;

    if (!apiKey) {
      console.error("OPENAI_API_KEY is missing");

      return res.status(500).json({
        error: "OPENAI_API_KEY is not configured"
      });
    }

    // Read the user's message.
    const body = req.body || {};
    const message = body.message;

    if (!message || typeof message !== "string") {
      return res.status(400).json({
        error: "Missing message"
      });
    }

    console.log("ATLAS request:", message);

    // Ask OpenAI.
    const openaiResponse = await fetch(
      "https://api.openai.com/v1/responses",
      {
        method: "POST",

        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${apiKey}`
        },

        body: JSON.stringify({
          model: "gpt-5.6",

          instructions:
            "You are ATLAS, a conversational AI assistant. " +
            "Be natural, helpful, concise, intelligent, and friendly. " +
            "Answer the user's request directly. " +
            "Do not claim to have performed actions you did not perform.",

          input: message
        })
      }
    );

    // Read OpenAI's response.
    const data = await openaiResponse.json();

    console.log("OpenAI status:", openaiResponse.status);

    // OpenAI returned an error.
    if (!openaiResponse.ok) {
      console.error("OpenAI error:", data);

      return res.status(openaiResponse.status).json({
        error:
          data?.error?.message ||
          "OpenAI request failed"
      });
    }

    // Get the generated answer.
    let reply = data.output_text;

    // Extra fallback in case output_text isn't available.
    if (!reply && Array.isArray(data.output)) {
      for (const item of data.output) {
        if (Array.isArray(item.content)) {
          for (const content of item.content) {
            if (
              content.type === "output_text" &&
              typeof content.text === "string"
            ) {
              reply = content.text;
              break;
            }
          }
        }

        if (reply) break;
      }
    }

    // Make sure we actually received an answer.
    if (!reply || typeof reply !== "string") {
      console.error("No usable reply:", data);

      return res.status(500).json({
        error: "OpenAI returned no usable response"
      });
    }

    console.log("ATLAS reply:", reply);

    // Send the answer back to the Android app.
    return res.status(200).json({
      reply: reply.trim()
    });

  } catch (error) {
    console.error("ATLAS SERVER ERROR:", error);

    return res.status(500).json({
      error: "Server error",
      details: error?.message || "Unknown error"
    });
  }
}