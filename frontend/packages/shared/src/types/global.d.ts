declare global {
  interface Window {
    __PLP_TOKEN_KEY__: string;
    __PLP_REFRESH_KEY__: string;
    __PLP_USER_KEY__: string;
    __ENV__?: {
      VITE_API_BASE_URL?: string;
    };
  }
}
export {};