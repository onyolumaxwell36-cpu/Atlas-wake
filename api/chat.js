
const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
const GROQ_MODEL = "llama-3.3-70b-versatile";

function cleanHistory(history) {
  if (!Array.isArray(history)) return [];

  return history
    .filter(
      (item) =>
        item &&
        ["user", "assistant"].includes(item.role) &&
        typeof item.content === "string"
    )
    .slice(-20)
    .map((item) => ({
      role: item.role,
      content: item.content.slice(0, 4000),
    }));
}

function looksLikeWeatherQuestion(message) {
  return /\b(weather|temperature|forecast|rain|raining|sunny|humidity|wind speed)\b/i.test(
    message
  );
}

function extractLocation(message) {
  const match = message.match(
    /\b(?:weather|temperature|forecast|rain|raining|sunny)\b.{0,40}?\b(?:in|at|for)\s+([a-zA-Z][a-zA-Z\s-]{1,50}?)(?:[?.!,]|$)/i
  );

  if (!match) return null;

  return match[1].trim().replace(/\s+/g, " ");
}

function weatherDescription(code) {
  const descriptions = {
    0: "clear sky",
    1: "mainly clear",
    2: "partly cloudy",
    3: "overcast",
    45: "foggy",
    48: "foggy with frost",
    51: "light drizzle",
    53: "moderate drizzle",
    55: "heavy drizzle",
    61: "light rain",
    63: "moderate rain",
    65: "heavy rain",
    71: "light snow",
    73: "moderate snow",
    75: "heavy snow",
    80: "light rain showers",
    81: "moderate rain showers",
    82: "heavy rain showers",
    95: "thunderstorm",
    96: "thunderstorm with hail",
    99: "severe thunderstorm with hail",
  };

  return descriptions[code] || "weather conditions unavailable";
}

async function getWeather(location) {
  if (!location) return null;

  try {
    const geoUrl = new URL(
      "https://geocoding-api.open-meteo.com/v1/search"
    );

    geoUrl.searchParams.set("name", location);
    geoUrl.searchParams.set("count", "1");
    geoUrl.searchParams.set("language", "en");
    geoUrl.searchParams.set("format", "json");

    const geoResponse = await fetch(geoUrl);
    if (!geoResponse.ok) return null;

    const geoData = await geoResponse.json();
    const place = geoData.results?.[0];

    if (!place) return null;

    const weatherUrl = new URL(
      "https://api.open-meteo.com/v1/forecast"
    );

    weatherUrl.searchParams.set("latitude", place.latitude);
    weatherUrl.searchParams.set("longitude", place.longitude);
    weatherUrl.searchParams.set(
      "current",
      "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m"
    );
    weatherUrl.searchParams.set("timezone", "auto");

    const weatherResponse = await fetch(weatherUrl);
    if (!weatherResponse.ok) return null;

    const weatherData = await weatherResponse.json();
    const current = weatherData.current;

    if (!current) return null;

    return {
      location: [place.name, place.admin1, place.country]
        .filter(Boolean)
        .join(", "),
      temperature: current.temperature_2m,
      feelsLike: current.apparent_temperature,
      humidity: current.relative_humidity_2m,
      precipitation: current.precipitation,
      windSpeed: current.wind_speed_10m,
      condition: weatherDescription(current.weather_code),
      time: current.time,
    };
  } catch (error) {
    console.error("Weather lookup failed:", error.message);
    return null;
  }
}

export default async function handler(req, res) {
  if (req.method !== "POST") {
    return res.status(405).json({
      reply: "Please send a POST request.",
      weatherUsed: false,
    });
  }

  try {
    const body = req.body || {};
    const message =
      typeof body.message === "string" ? body.message.trim() : "";

    if (!message) {
      return res.status(400).json({
        reply: "Please tell me what you need, and I'll help.",
        weatherUsed: false,
      });
    }

    const apiKey = process.env.GROQ_API_KEY;

    if (!apiKey) {
      console.error("GROQ_API_KEY is not configured.");
      return res.status(500).json({
        reply: "My AI connection is not configured yet. Please check my server settings.",
        weatherUsed: false,
      });
    }

    const history = cleanHistory(body.history);
    let weatherContext = "";
    let weatherUsed = false;

    if (looksLikeWeatherQuestion(message)) {
      const location = extractLocation(message);

      if (location) {
        const weather = await getWeather(location);

        if (weather) {
          weatherUsed = true;
          weatherContext = `
Current weather information:
Location: ${weather.location}
Conditions: ${weather.condition}
Temperature: ${weather.temperature}°C
Feels like: ${weather.feelsLike}°C
Humidity: ${weather.humidity}%
Precipitation: ${weather.precipitation} mm
Wind speed: ${weather.windSpeed} km/h
Local observation time: ${weather.time}

Use these values when answering the user's weather question.
Do not claim these values are a forecast; they describe current conditions.
`;
        } else {
          weatherContext =
            "The weather lookup did not return reliable data. Be honest about this.";
        }
      } else {
        weatherContext =
          "The user asked about weather but did not specify a location. Ask which location they mean.";
      }
    }

    const messages = [
      {
        role: "system",
        content: `You are ATLAS, Maxwell's personal AI voice assistant.

Be helpful, accurate, friendly, and conversational.
Keep answers clear and reasonably concise because your responses may be spoken aloud.
Use the conversation history to understand follow-up questions.
Never claim to have opened an app, changed a phone setting, sent a message, or performed another device action unless the application confirms it.
You do not automatically have control over the user's phone or apps.
If you do not know something, say so rather than inventing facts.

${weatherContext}`,
      },
      ...history,
      {
        role: "user",
        content: message,
      },
    ];

    const response = await fetch(GROQ_URL, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: GROQ_MODEL,
        messages,
        temperature: 0.6,
        max_completion_tokens: 700,
      }),
    });

    const result = await response.json();

    if (!response.ok) {
      console.error(
        "Groq API error:",
        response.status,
        result.error?.message || "Unknown provider error"
      );

      return res.status(502).json({
        reply:
          response.status === 429
            ? "I'm temporarily unable to answer because of an API usage limit. Please try again later."
            : "I couldn't connect to my AI service. Please check the server settings and try again.",
        weatherUsed,
      });
    }

    const reply = result.choices?.[0]?.message?.content?.trim();

    if (!reply) {
      return res.status(502).json({
        reply: "I received an empty response. Please try again.",
        weatherUsed,
      });
    }

    return res.status(200).json({
      reply,
      weatherUsed,
    });
  } catch (error) {
    console.error("ATLAS server error:", error.message);

    return res.status(500).json({
      reply: "Something went wrong while I was processing your request. Please try again.",
      weatherUsed: false,
    });
  }
}
