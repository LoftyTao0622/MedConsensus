import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import "./styles.css";

function FatalError({ error }) {
  return (
    <div
      style={{
        minHeight: "100vh",
        padding: "32px",
        background: "#eef6fb",
        color: "#12324a",
        fontFamily: '"Manrope", "Noto Sans SC", sans-serif'
      }}
    >
      <h1 style={{ marginTop: 0 }}>前端启动失败</h1>
      <p>浏览器捕获到了一个运行时错误，详情如下：</p>
      <pre
        style={{
          whiteSpace: "pre-wrap",
          background: "#ffffff",
          borderRadius: "16px",
          padding: "16px",
          border: "1px solid #cfe0ee"
        }}
      >
        {error?.stack || error?.message || String(error)}
      </pre>
    </div>
  );
}

class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error) {
    console.error("React runtime error:", error);
  }

  render() {
    if (this.state.error) {
      return <FatalError error={this.state.error} />;
    }

    return this.props.children;
  }
}

window.addEventListener("error", (event) => {
  console.error("Global error:", event.error || event.message);
});

window.addEventListener("unhandledrejection", (event) => {
  console.error("Unhandled promise rejection:", event.reason);
});

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <ErrorBoundary>
      <App />
    </ErrorBoundary>
  </React.StrictMode>
);
