// Runtime API URL: localhost for Docker Desktop; same-origin /plp-api behind host nginx (Credinnov EC2).
(function () {
  var h = typeof window !== 'undefined' ? window.location.hostname : '';
  var useLocalGateway =
    h === 'localhost' || h === '127.0.0.1' || h === '';
  window.__ENV__ = {
    VITE_API_BASE_URL: useLocalGateway ? 'http://localhost:8180' : '/plp-api',
  };
})();
