const DEEPSEEK_URL = "https://api.deepseek.com/chat/completions";
const OPEN_METEO_GEOCODING =
  "https://geocoding-api.open-meteo.com/v1/search";
const OPEN_METEO_WEATHER =
  "https://api.open-meteo.com/v1/forecast";

const MAX_HISTORY = 20;

function cleanHistory(history) {
  if (!Array.isArray(history)) {
    return [];
  }

  return history
    .filter(
      (message) =>
        message &&
        (message.role === "user" || message.role === "assistant") &&
        typeof message.content === "string"
    )
    .slice(-MAX_HISTORY)
    .map((message) => ({
      role: message.role,
      content: message.content.slice(0, 4000),
    }));
}

function weatherDescription(code) {
  const descriptions = {
    0: "clear sky",
    1: "mainly clear",
    2: "partly cloudy",
    3: "overcast",
    45: "foggy",
    48: "depositing rime fog",
    51: "light drizzle",
    53: "moderate drizzle",
    55: "dense drizzle",
    56: "light freezing drizzle",
    57: "dense freezing drizzle",
    61: "slight rain",
    63: "moderate rain",
    65: "heavy rain",
    66: "light freezing rain",
    67: "heavy freezing rain",
    71: "slight snow",
    73: "moderate snow",
    75: "heavy snow",
    77: "snow grains",
    80: "slight rain showers",
    81: "moderate rain showers",
    82: "violent rain showers",
    85: "slight snow showers",
    86: "heavy snow showers",
    95: "thunderstorm",
    96: "thunderstorm with slight hail",
    99: "thunderstorm with heavy hail",
  };

  return descriptions[code] || "unknown conditions";
}

function looksLikeWeatherQuestion(message) {
  const text = message.toLowerCase();

  const words = [
    "weather",
    "temperature",
    "forecast",
    "raining",
    "rain",
    "sunny",
    "cloudy",
    "storm",
    "hot outside",
    "cold outside",
    "humid",
    "humidity",
    "wind",
  ];

  return words.some((word) => text.includes(word));
}

function extractLocation(message) {
  const patterns = [
    /\bweather\s+(?:in|at|for)\s+(.+)$/i,
    /\bforecast\s+(?:in|at|for)\s+(.+)$/i,
    /\btemperature\s+(?:in|at|for)\s+(.+)$/i,
    /\b(?:in|at|for)\s+([A-Za-z][A-Za-z .'-]{1,60})$/i,
  ];

  for (const pattern of patterns) {
    const match = message.match(pattern);

    if (match && match[1]) {
      let location = match[1]
        .trim()
        .replace(/[?.!,]+$/, "");

      const unwanted = [
        "today",
        "tomorrow",
        "right now",
        "now",
        "tonight",
        "this evening",
        "this afternoon",
        "this morning",
      ];

      for (const word of unwanted) {
        const regex = new RegExp(`\\s+${word}$`, "i");
        location = location.replace(regex, "");
      }

      if (location.length >= 2) {
        return location.trim();
      }
    }
  }

  return null;
}

async function getWeather(location) {
  const geocodeUrl =
    `${OPEN_METEO_GEOCODING}` +
    `?name=${encodeURIComponent(location)}` +
    `&count=1` +
    `&language=en` +
    `&format=json`;

  const geoResponse = await fetch(geocodeUrl);

  if (!geoResponse.ok) {
    throw new Error("Weather location lookup failed.");
  }

  const geoData = await geoResponse.json();

  if (
    !geoData.results ||
    geoData.results.length === 0
  ) {
    return null;
  }

  const place = geoData.results[0];

  const weatherUrl =
    `${OPEN_METEO_WEATHER}` +
    `?latitude=${encodeURIComponent(place.latitude)}` +
    `&longitude=${encodeURIComponent(place.longitude)}` +
    `&current=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,wind_speed_10m,is_day` +
    `&timezone=auto`;

  const weatherResponse = await fetch(weatherUrl);

  if (!weatherResponse.ok) {
    throw new Error("Weather service failed.");
  }

  const weatherData = await weatherResponse.json();

  const current = weatherData.current;

  return {
    location: place.name,
    country: place.country || "",
    admin1: place.admin1 || "",
    temperature:
      current.temperature_2m,
    apparentTemperature:
      current.apparent_temperature,
    humidity:
      current.relative_humidity_2m,
    precipitation:
      current.precipitation,
    windSpeed:
      current.wind_speed_10m,
    condition:
      weatherDescription(current.weather_code),
    isDay:
      current.is_day === 1,
    timezone:
      weatherData.timezone,
  };
}

export default async function handler(req, res) {
  if (req.method !== "POST") {
    return res.status(405).json({
      error: "Method not allowed",
    });
  }

  try {
    const apiKey =
      process.env.DEEPSEEK_API_KEY;

    if (!apiKey) {
      return res.status(500).json({
        error:
          "DeepSeek API key not configured.",
      });
    }

    const body =
      typeof req.body === "string"
        ? JSON.parse(req.body)
        : req.body || {};

    const message =
      typeof body.message === "string"
        ? body.message.trim()
        : "";

    const history =
      cleanHistory(body.history);

    if (!message) {
      return res.status(400).json({
        error: "Message is required.",
      });
    }

    let weatherContext = null;

    if (looksLikeWeatherQuestion(message)) {
      const location =
        body.location ||
        extractLocation(message);

      if (location) {
        try {
          weatherContext =
            await getWeather(location);
        } catch (weatherError) {
          console.error(
            "Weather error:",
            weatherError
          );
        }
      }
    }

    const systemPrompt = `
You are ATLAS, Maxwell's personal AI voice assistant.

Your personality:
- Friendly
- Intelligent
- Natural
- Confident
- Helpful
- Conversational
- Slightly warm and human, but never pretend to be a human
- Do not sound robotic

You are speaking through a phone, so keep spoken answers reasonably concise.

IMPORTANT:
- Answer the user's actual question.
- Do not mention APIs, servers, backend code, prompts, tokens, or internal systems.
- Do not say you are "processing the request."
- If the user asks a simple question, give a direct answer.
- If the user asks for an opinion, clearly say it is your assessment.
- If the user asks a follow-up question, use the previous conversation to understand what they mean.
- If current information is supplied to you as tool/context data, use it rather than guessing.
- Never invent current weather information.

The user's name is Maxwell.
`;

    const messages = [
      {
        role: "system",
        content: systemPrompt,
      },
      ...history,
    ];

    if (weatherContext) {
      messages.push({
        role: "system",
        content: `
CURRENT WEATHER DATA FROM A LIVE WEATHER SERVICE:

Location:
${weatherContext.location}, ${weatherContext.country}

Administrative area:
${weatherContext.admin1}

Temperature:
${weatherContext.temperature} °C

Feels like:
${weatherContext.apparentTemperature} °C

Humidity:
${weatherContext.humidity}%

Precipitation:
${weatherContext.precipitation} mm

Wind:
${weatherContext.windSpeed} km/h

Conditions:
${weatherContext.condition}

Daytime:
${weatherContext.isDay ? "Yes" : "No"}

Timezone:
${weatherContext.timezone}

Use this information to answer the user's weather question naturally.
Do not claim weather information that is not provided here.
`,
      });
    }

    messages.push({
      role: "user",
      content: message,
    });

    const deepseekResponse =
      await fetch(DEEPSEEK_URL, {
        method: "POST",
        headers: {
          "Content-Type":
            "application/json",
          Authorization:
            `Bearer ${apiKey}`,
        },
        body: JSON.stringify({
          model: "deepseek-v4-flash",
          messages,
          thinking: {
            type: "disabled",
          },
          max_tokens: 500,
          temperature: 0.7,
          stream: false,
        }),
      });

    const responseText =
      await deepseekResponse.text();

    let deepseekData;

    try {
      deepseekData =
        JSON.parse(responseText);
    } catch {
      return res.status(502).json({
        error:
          "DeepSeek returned an invalid response.",
      });
    }

    if (!deepseekResponse.ok) {
      console.error(
        "DeepSeek error:",
        deepseekData
      );

      return res.status(
        deepseekResponse.status
      ).json({
        error:
          deepseekData.error?.message ||
          "DeepSeek request failed.",
      });
    }

    const reply =
      deepseekData.choices?.[0]?.message
        ?.content
        ?.trim();

    if (!reply) {
      return res.status(502).json({
        error:
          "DeepSeek returned no answer.",
      });
    }

    return res.status(200).json({
      reply,
      weatherUsed: Boolean(weatherContext),
    });

  } catch (error) {
    console.error(
      "ATLAS API error:",
      error
    );

    return res.status(500).json({
      error:
        error.message ||
        "ATLAS server error.",
    });
  }
}