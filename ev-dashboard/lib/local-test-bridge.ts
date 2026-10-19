import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { createInterface } from "node:readline";

type BridgeResponse = {
  id: string;
  status: number;
  headers: Record<string, string[]>;
  body: string;
};

type PendingRequest = {
  resolve: (value: BridgeResponse) => void;
  reject: (error: Error) => void;
};

class LocalTestBridge {
  private process: ChildProcessWithoutNullStreams | null = null;
  private pending = new Map<string, PendingRequest>();
  private ready: Promise<void> | null = null;

  private start(): Promise<void> {
    if (this.ready) {
      return this.ready;
    }

    this.ready = this.startProcess();
    return this.ready;
  }

  private async startProcess(): Promise<void> {
    const classpathFile = process.env.EV_LOCAL_TEST_CLASSPATH;

    if (!classpathFile || process.env.NODE_ENV === "production") {
      throw new Error("The local test bridge is disabled");
    }

    const classpath = (await readFile(classpathFile, "utf8")).trim();
    const child = spawn(
      "java",
      ["-cp", classpath, "fr.codetics.ev_server.LocalDashboardHarnessKt"],
      { cwd: process.cwd().replace(/\/ev-dashboard$/, "") },
    );
    this.process = child;

    return new Promise((resolve, reject) => {
      const lines = createInterface({ input: child.stdout });

      lines.on("line", (line) => {
        if (line === "EV_TEST_READY") {
          resolve();
        }

        if (line.startsWith("EV_TEST_RESPONSE:")) {
          const response = JSON.parse(line.slice("EV_TEST_RESPONSE:".length));
          this.pending.get(response.id)?.resolve(response);
          this.pending.delete(response.id);
        }
      });

      child.stderr.on("data", () => {
        // Keep test transport output separate from browser response bodies.
      });

      child.on("exit", () => {
        const error = new Error("The local Spring test harness stopped");
        reject(error);
        this.pending.forEach((request) => request.reject(error));
        this.pending.clear();
        this.process = null;
        this.ready = null;
      });

      child.on("error", reject);
    });
  }

  async request(input: object): Promise<BridgeResponse> {
    await this.start();
    const id = randomUUID();

    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error("The local test request timed out"));
      }, 20000);

      this.pending.set(id, {
        resolve: (response) => {
          clearTimeout(timeout);
          resolve(response);
        },
        reject: (error) => {
          clearTimeout(timeout);
          reject(error);
        },
      });
      this.process?.stdin.write(`${JSON.stringify({ ...input, id })}\n`);
    });
  }
}

const bridgeGlobal = globalThis as typeof globalThis & {
  evLocalTestBridge?: LocalTestBridge;
};

export function localTestRequest(input: object): Promise<BridgeResponse> {
  bridgeGlobal.evLocalTestBridge ||= new LocalTestBridge();
  return bridgeGlobal.evLocalTestBridge.request(input);
}
