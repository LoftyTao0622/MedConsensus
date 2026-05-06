import { Client } from "@stomp/stompjs";

function resolveBrokerUrl() {
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${window.location.host}/ws/diagnosis`;
}

export function connectPipelineSocket(onMessage) {
  const client = new Client({
    brokerURL: resolveBrokerUrl(),
    reconnectDelay: 3000
  });

  client.onConnect = () => {
    client.subscribe("/topic/pipeline", (message) => {
      onMessage(JSON.parse(message.body));
    });
  };

  client.onStompError = (frame) => {
    console.error("STOMP error:", frame.headers.message, frame.body);
  };

  client.onWebSocketError = (event) => {
    console.error("WebSocket error:", event);
  };

  client.activate();
  return () => client.deactivate();
}
