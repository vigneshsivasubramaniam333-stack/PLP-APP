// Runtime environment config - overridden at deployment.
// Local dev: call PLP API gateway directly (CORS enabled); avoids Vite proxy issues with Spring Gateway.
window.__ENV__ = {
  VITE_API_BASE_URL: "http://127.0.0.1:8180",
};
