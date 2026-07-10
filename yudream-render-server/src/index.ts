import { buildServer } from "./server.js";
import { serverConfig } from "./config.js";

const app = buildServer();
await app.listen(serverConfig);
