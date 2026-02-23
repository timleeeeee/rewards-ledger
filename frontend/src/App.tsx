import { FormEvent, useEffect, useMemo, useState } from "react";
import { ApiHttpError, ledgerApi } from "./api/client";
import { clearSession, getSessionState, setSessionFromAuth } from "./api/session";
import type {
  AccountResponse,
  ApiCall,
  AuthUser,
  HealthResponse,
  TransactionPageResponse,
  TransactionResponse
} from "./api/types";
import { Panel } from "./components/Panel";
import { formatIso, nextIdempotencyKey, nextRequestId, shortId } from "./lib/format";

type Tab = "wallet" | "ops";
type AuthMode = "login" | "register";

type Notice = {
  tone: "success" | "error";
  message: string;
};

type ActivityEntry = {
  id: string;
  at: string;
  label: string;
  method: string;
  path: string;
  status: number;
  requestId: string | null;
  summary: string;
};

function toPretty(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

function parseError(error: unknown): {
  status: number;
  code: string;
  message: string;
  requestId: string | null;
  summary: string;
  method: string;
  path: string;
} {
  if (error instanceof ApiHttpError) {
    return {
      status: error.status,
      code: error.payload?.code ?? "API_ERROR",
      message: error.payload?.message ?? error.message,
      requestId: error.requestId,
      summary: toPretty(error.payload ?? { message: error.message }),
      method: error.method,
      path: error.path
    };
  }

  return {
    status: 0,
    code: "UNEXPECTED",
    message: "Unexpected client error",
    requestId: null,
    summary: toPretty(error),
    method: "N/A",
    path: "N/A"
  };
}

function App() {
  const [activeTab, setActiveTab] = useState<Tab>("wallet");
  const [currentUser, setCurrentUser] = useState<AuthUser | null>(() => getSessionState()?.user ?? null);
  const [authMode, setAuthMode] = useState<AuthMode>("login");
  const [authEmail, setAuthEmail] = useState("");
  const [authPassword, setAuthPassword] = useState("");

  const [accounts, setAccounts] = useState<string[]>([]);
  const [selectedAccountId, setSelectedAccountId] = useState<string>("");
  const [account, setAccount] = useState<AccountResponse | null>(null);
  const [transactions, setTransactions] = useState<TransactionResponse[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);

  const [earnAmount, setEarnAmount] = useState("100");
  const [spendAmount, setSpendAmount] = useState("40");
  const [transferAmount, setTransferAmount] = useState("25");
  const [reason, setReason] = useState("");
  const [currency, setCurrency] = useState("PTS");
  const [transferTargetId, setTransferTargetId] = useState("");

  const [reversalTransactionId, setReversalTransactionId] = useState("");
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [idempotencyDemoResult, setIdempotencyDemoResult] = useState("");
  const [insufficientFundsDemoResult, setInsufficientFundsDemoResult] = useState("");

  const [busyKey, setBusyKey] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [lastPayload, setLastPayload] = useState<string>("");
  const [activity, setActivity] = useState<ActivityEntry[]>([]);

  const availableTargets = useMemo(
    () => accounts.filter((id) => id !== selectedAccountId),
    [accounts, selectedAccountId]
  );

  function resetWalletState(): void {
    setAccounts([]);
    setSelectedAccountId("");
    setAccount(null);
    setTransactions([]);
    setNextCursor(null);
    setTransferTargetId("");
    setReversalTransactionId("");
    setIdempotencyDemoResult("");
    setInsufficientFundsDemoResult("");
  }

  useEffect(() => {
    let alive = true;
    const bootstrap = async () => {
      const session = getSessionState();
      if (!session) {
        return;
      }
      try {
        const me = await ledgerApi.me(nextRequestId());
        if (alive) {
          setCurrentUser(me.data);
        }
      } catch {
        clearSession();
        if (alive) {
          setCurrentUser(null);
          resetWalletState();
        }
      }
    };
    void bootstrap();
    return () => {
      alive = false;
    };
  }, []);

  function appendActivity(entry: ActivityEntry): void {
    setActivity((prev) => [entry, ...prev].slice(0, 25));
  }

  function markSuccess<T>(label: string, apiCall: ApiCall<T>, payloadOverride?: unknown): void {
    const summary = toPretty(payloadOverride ?? apiCall.data);
    setLastPayload(summary);
    setNotice({ tone: "success", message: `${label} succeeded (${apiCall.status})` });
    appendActivity({
      id: crypto.randomUUID(),
      at: new Date().toISOString(),
      label,
      method: apiCall.method,
      path: apiCall.path,
      status: apiCall.status,
      requestId: apiCall.requestId,
      summary
    });
  }

  function markFailure(label: string, error: unknown): void {
    const parsed = parseError(error);
    setLastPayload(parsed.summary);

    if (parsed.status === 401) {
      clearSession();
      setCurrentUser(null);
      resetWalletState();
      setNotice({ tone: "error", message: "Session expired or unauthorized. Please sign in again." });
    } else {
      setNotice({
        tone: "error",
        message: `${label} failed (${parsed.status}) ${parsed.code}: ${parsed.message}`
      });
    }

    appendActivity({
      id: crypto.randomUUID(),
      at: new Date().toISOString(),
      label,
      method: parsed.method,
      path: parsed.path,
      status: parsed.status,
      requestId: parsed.requestId,
      summary: parsed.summary
    });
  }

  async function withBusy(key: string, work: () => Promise<void>): Promise<void> {
    setBusyKey(key);
    setNotice(null);
    try {
      await work();
    } catch (error) {
      markFailure(key, error);
    } finally {
      setBusyKey(null);
    }
  }

  async function refreshAccount(accountId = selectedAccountId): Promise<void> {
    if (!accountId || !currentUser) {
      return;
    }

    const accountResponse = await ledgerApi.getAccount(accountId, nextRequestId());
    const txResponse = await ledgerApi.listTransactions(accountId, 12, null, nextRequestId());

    setAccount(accountResponse.data);
    setTransactions(txResponse.data.items);
    setNextCursor(txResponse.data.nextCursor ?? null);
    markSuccess("Refresh account", accountResponse, {
      account: accountResponse.data,
      transactionsLoaded: txResponse.data.items.length,
      nextCursor: txResponse.data.nextCursor
    });
  }

  async function submitAuth(event: FormEvent): Promise<void> {
    event.preventDefault();
    await withBusy("auth", async () => {
      const response = authMode === "register"
        ? await ledgerApi.register(authEmail.trim(), authPassword, nextRequestId())
        : await ledgerApi.login(authEmail.trim(), authPassword, nextRequestId());

      setSessionFromAuth(response.data);
      setCurrentUser(response.data.user);
      setAuthPassword("");
      markSuccess(authMode === "register" ? "Register" : "Login", response, {
        user: response.data.user,
        accessTokenExpiresInSeconds: response.data.accessTokenExpiresInSeconds
      });
    });
  }

  async function logout(): Promise<void> {
    await withBusy("logout", async () => {
      const session = getSessionState();
      if (session?.refreshToken) {
        try {
          await ledgerApi.logout(session.refreshToken, nextRequestId());
        } catch {
          // Logout should still clear local auth state even if backend token is already invalid.
        }
      }
      clearSession();
      setCurrentUser(null);
      resetWalletState();
      setNotice({ tone: "success", message: "Signed out." });
    });
  }

  async function createAccount(): Promise<void> {
    if (!currentUser) {
      setNotice({ tone: "error", message: "Sign in before creating accounts." });
      return;
    }
    await withBusy("create-account", async () => {
      const created = await ledgerApi.createAccount(nextRequestId());
      setAccounts((prev) => (prev.includes(created.data.id) ? prev : [created.data.id, ...prev]));
      setSelectedAccountId(created.data.id);
      if (!transferTargetId) {
        setTransferTargetId(created.data.id);
      }
      markSuccess("Create account", created);
      await refreshAccount(created.data.id);
    });
  }

  async function onSelectAccount(accountId: string): Promise<void> {
    setSelectedAccountId(accountId);
    await withBusy("switch-account", async () => {
      await refreshAccount(accountId);
    });
  }

  async function loadMoreTransactions(): Promise<void> {
    if (!selectedAccountId || !nextCursor) {
      return;
    }

    await withBusy("load-more-transactions", async () => {
      const response: ApiCall<TransactionPageResponse> = await ledgerApi.listTransactions(
        selectedAccountId,
        12,
        nextCursor,
        nextRequestId()
      );
      setTransactions((prev) => [...prev, ...response.data.items]);
      setNextCursor(response.data.nextCursor ?? null);
      markSuccess("Load more transactions", response, {
        fetched: response.data.items.length,
        nextCursor: response.data.nextCursor
      });
    });
  }

  async function earnPoints(event: FormEvent): Promise<void> {
    event.preventDefault();
    if (!selectedAccountId) {
      setNotice({ tone: "error", message: "Select an account before earning points." });
      return;
    }

    await withBusy("earn", async () => {
      const response = await ledgerApi.earn(
        selectedAccountId,
        {
          amount: Number(earnAmount),
          reason: reason || undefined,
          currency: currency || undefined
        },
        nextIdempotencyKey("earn"),
        nextRequestId()
      );
      markSuccess("Earn points", response);
      await refreshAccount(selectedAccountId);
    });
  }

  async function spendPoints(event: FormEvent): Promise<void> {
    event.preventDefault();
    if (!selectedAccountId) {
      setNotice({ tone: "error", message: "Select an account before spending points." });
      return;
    }

    await withBusy("spend", async () => {
      const response = await ledgerApi.spend(
        selectedAccountId,
        {
          amount: Number(spendAmount),
          reason: reason || undefined,
          currency: currency || undefined
        },
        nextIdempotencyKey("spend"),
        nextRequestId()
      );
      markSuccess("Spend points", response);
      await refreshAccount(selectedAccountId);
    });
  }

  async function transferPoints(event: FormEvent): Promise<void> {
    event.preventDefault();
    if (!selectedAccountId || !transferTargetId) {
      setNotice({ tone: "error", message: "Pick both source and target accounts for transfer." });
      return;
    }

    await withBusy("transfer", async () => {
      const response = await ledgerApi.transfer(
        {
          fromAccountId: selectedAccountId,
          toAccountId: transferTargetId,
          amount: Number(transferAmount),
          reason: reason || undefined,
          currency: currency || undefined
        },
        nextIdempotencyKey("transfer"),
        nextRequestId()
      );
      markSuccess("Transfer points", response);
      await refreshAccount(selectedAccountId);
    });
  }

  async function reverseTransaction(event: FormEvent): Promise<void> {
    event.preventDefault();
    if (!selectedAccountId || !reversalTransactionId) {
      setNotice({ tone: "error", message: "Select account and transaction ID for reversal." });
      return;
    }

    await withBusy("reversal", async () => {
      const response = await ledgerApi.reversal(
        selectedAccountId,
        { originalTransactionId: reversalTransactionId.trim(), reason: reason || undefined },
        nextIdempotencyKey("reversal"),
        nextRequestId()
      );
      markSuccess("Reverse transaction", response);
      setReversalTransactionId("");
      await refreshAccount(selectedAccountId);
    });
  }

  async function runIdempotencyDemo(): Promise<void> {
    if (!selectedAccountId) {
      setNotice({ tone: "error", message: "Select an account before running idempotency demo." });
      return;
    }

    await withBusy("idempotency-demo", async () => {
      const sharedKey = nextIdempotencyKey("idem-demo");
      const first = await ledgerApi.earn(
        selectedAccountId,
        { amount: 7, reason: "idempotency-demo", currency: currency || undefined },
        sharedKey,
        nextRequestId()
      );
      const second = await ledgerApi.earn(
        selectedAccountId,
        { amount: 7, reason: "idempotency-demo", currency: currency || undefined },
        sharedKey,
        nextRequestId()
      );

      const same = first.data.id === second.data.id;
      setIdempotencyDemoResult(
        same
          ? `Success: replay returned same transaction ${first.data.id}`
          : `Mismatch: first=${first.data.id}, second=${second.data.id}`
      );

      markSuccess("Idempotency demo", first, {
        firstTransactionId: first.data.id,
        secondTransactionId: second.data.id,
        sameResult: same,
        idempotencyKey: sharedKey
      });

      await refreshAccount(selectedAccountId);
    });
  }

  async function runInsufficientFundsDemo(): Promise<void> {
    if (!selectedAccountId) {
      setNotice({ tone: "error", message: "Select an account before running error demo." });
      return;
    }

    await withBusy("insufficient-funds-demo", async () => {
      const amount = (account?.balance ?? 0) + 100_000;
      try {
        await ledgerApi.spend(
          selectedAccountId,
          { amount, reason: "insufficient-funds-demo", currency: currency || undefined },
          nextIdempotencyKey("insufficient-funds-demo"),
          nextRequestId()
        );
        setInsufficientFundsDemoResult("Unexpected: spend succeeded");
      } catch (error) {
        const parsed = parseError(error);
        setInsufficientFundsDemoResult(
          `Expected failure -> status=${parsed.status}, code=${parsed.code}, requestId=${parsed.requestId ?? "none"}`
        );
        throw error;
      }
    });
  }

  async function refreshHealth(): Promise<void> {
    await withBusy("health", async () => {
      const response = await ledgerApi.health(nextRequestId());
      setHealth(response.data);
      markSuccess("Health check", response);
    });
  }

  return (
    <div className="app-shell">
      <header className="hero">
        <p className="eyebrow">Rewards Ledger Console</p>
        <h1>Wallet + Ops Demo</h1>
        <p>
          Frontend demo to exercise account, ledger, reliability, and tracing behaviors against the
          Spring Boot backend.
        </p>
        <p className="muted">Demo environment only. Do not enter real personal data (PII).</p>
        {currentUser ? (
          <p className="muted">Signed in as <span className="mono">{currentUser.email}</span></p>
        ) : (
          <p className="muted">Sign in to run wallet and write operations.</p>
        )}
      </header>

      {notice ? <div className={`notice ${notice.tone}`}>{notice.message}</div> : null}

      {!currentUser ? (
        <main className="grid">
          <div className="left-column">
            <Panel title="Authentication" subtitle="Sign in to access wallet operations">
              <form className="action-form" onSubmit={(event) => void submitAuth(event)}>
                <h3>{authMode === "login" ? "Login" : "Register"}</h3>
                <label>
                  Email
                  <input
                    type="email"
                    required
                    value={authEmail}
                    onChange={(event) => setAuthEmail(event.target.value)}
                    placeholder="demo@example.com"
                  />
                </label>
                <label>
                  Password
                  <input
                    type="password"
                    required
                    value={authPassword}
                    onChange={(event) => setAuthPassword(event.target.value)}
                    placeholder="at least 8 characters"
                  />
                </label>
                <div className="button-row">
                  <button type="submit" disabled={busyKey !== null}>
                    {busyKey === "auth" ? "Submitting..." : authMode === "login" ? "Login" : "Register"}
                  </button>
                  <button
                    type="button"
                    onClick={() => setAuthMode((prev) => (prev === "login" ? "register" : "login"))}
                    disabled={busyKey !== null}
                  >
                    {authMode === "login" ? "Need an account?" : "Have an account?"}
                  </button>
                </div>
              </form>
            </Panel>
          </div>

          <div className="right-column">
            <Panel title="Health / Status" subtitle="Public backend health endpoint">
              <div className="button-row">
                <button type="button" onClick={() => void refreshHealth()} disabled={busyKey !== null}>
                  Refresh Health
                </button>
              </div>
              {health ? <pre className="payload">{toPretty(health)}</pre> : <p className="muted">No health data loaded.</p>}
            </Panel>

            <Panel title="Last API Result" subtitle="Detailed payload for latest operation">
              <pre className="payload">{lastPayload || "No operations yet."}</pre>
            </Panel>
          </div>
        </main>
      ) : (
        <>
          <div className="tabs">
            <button
              className={activeTab === "wallet" ? "tab active" : "tab"}
              type="button"
              onClick={() => setActiveTab("wallet")}
            >
              Wallet
            </button>
            <button
              className={activeTab === "ops" ? "tab active" : "tab"}
              type="button"
              onClick={() => setActiveTab("ops")}
            >
              Ops / Debug
            </button>
            <button type="button" className="tab" onClick={() => void logout()} disabled={busyKey !== null}>
              Logout
            </button>
          </div>

          <main className="grid">
            <div className="left-column">
              <Panel title="Account Dashboard" subtitle="Create and switch wallet accounts">
                <div className="button-row">
                  <button type="button" onClick={() => void createAccount()} disabled={busyKey !== null}>
                    {busyKey === "create-account" ? "Creating..." : "Create Account"}
                  </button>
                  <button
                    type="button"
                    onClick={() => void refreshAccount()}
                    disabled={!selectedAccountId || busyKey !== null}
                  >
                    Refresh
                  </button>
                </div>

                <label className="field">
                  <span>Selected Account</span>
                  <select
                    value={selectedAccountId}
                    onChange={(event) => void onSelectAccount(event.target.value)}
                    disabled={accounts.length === 0 || busyKey !== null}
                  >
                    <option value="">Select an account</option>
                    {accounts.map((id) => (
                      <option key={id} value={id}>
                        {shortId(id)}
                      </option>
                    ))}
                  </select>
                </label>

                {account ? (
                  <dl className="data-list">
                    <div>
                      <dt>Status</dt>
                      <dd>{account.status}</dd>
                    </div>
                    <div>
                      <dt>Balance</dt>
                      <dd>{account.balance} PTS</dd>
                    </div>
                    <div>
                      <dt>Created</dt>
                      <dd>{formatIso(account.createdAt)}</dd>
                    </div>
                    <div>
                      <dt>Account ID</dt>
                      <dd className="mono">{account.id}</dd>
                    </div>
                  </dl>
                ) : (
                  <p className="muted">Create or select an account to load wallet data.</p>
                )}
              </Panel>

              <Panel title="Actions" subtitle="Earn, spend, or transfer points">
                <form className="action-form" onSubmit={(event) => void earnPoints(event)}>
                  <h3>Earn</h3>
                  <label>
                    Amount
                    <input type="number" min="1" value={earnAmount} onChange={(e) => setEarnAmount(e.target.value)} />
                  </label>
                  <button type="submit" disabled={busyKey !== null}>Earn Points</button>
                </form>

                <form className="action-form" onSubmit={(event) => void spendPoints(event)}>
                  <h3>Spend</h3>
                  <label>
                    Amount
                    <input type="number" min="1" value={spendAmount} onChange={(e) => setSpendAmount(e.target.value)} />
                  </label>
                  <button type="submit" disabled={busyKey !== null}>Spend Points</button>
                </form>

                <form className="action-form" onSubmit={(event) => void transferPoints(event)}>
                  <h3>Transfer</h3>
                  <label>
                    Target Account
                    <select
                      value={transferTargetId}
                      onChange={(e) => setTransferTargetId(e.target.value)}
                      disabled={availableTargets.length === 0}
                    >
                      <option value="">Select target</option>
                      {availableTargets.map((id) => (
                        <option key={id} value={id}>
                          {shortId(id)}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Amount
                    <input
                      type="number"
                      min="1"
                      value={transferAmount}
                      onChange={(e) => setTransferAmount(e.target.value)}
                    />
                  </label>
                  <button type="submit" disabled={busyKey !== null}>Transfer Points</button>
                </form>

                <div className="inline-fields">
                  <label>
                    Reason
                    <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="optional" />
                  </label>
                  <label>
                    Currency
                    <input value={currency} onChange={(e) => setCurrency(e.target.value)} placeholder="PTS" />
                  </label>
                </div>
              </Panel>

              {activeTab === "ops" ? (
                <Panel title="Ops / Debug" subtitle="Operational controls and reliability checks">
                  <form className="action-form" onSubmit={(event) => void reverseTransaction(event)}>
                    <h3>Reverse Transaction</h3>
                    <label>
                      Original Transaction ID
                      <input
                        value={reversalTransactionId}
                        onChange={(e) => setReversalTransactionId(e.target.value)}
                        placeholder="UUID"
                      />
                    </label>
                    <button type="submit" disabled={busyKey !== null}>Create Reversal</button>
                  </form>

                  <div className="button-row">
                    <button type="button" onClick={() => void runIdempotencyDemo()} disabled={busyKey !== null}>
                      Run Idempotency Replay Demo
                    </button>
                    <button type="button" onClick={() => void runInsufficientFundsDemo()} disabled={busyKey !== null}>
                      Trigger Insufficient Funds
                    </button>
                    <button type="button" onClick={() => void refreshHealth()} disabled={busyKey !== null}>
                      Refresh Health
                    </button>
                  </div>

                  {idempotencyDemoResult ? <p className="hint">{idempotencyDemoResult}</p> : null}
                  {insufficientFundsDemoResult ? <p className="hint">{insufficientFundsDemoResult}</p> : null}

                  {health ? (
                    <pre className="payload">{toPretty(health)}</pre>
                  ) : (
                    <p className="muted">Health data not loaded yet.</p>
                  )}
                </Panel>
              ) : null}
            </div>

            <div className="right-column">
              <Panel title="Recent Transactions" subtitle="Cursor-based history for selected account">
                {transactions.length === 0 ? (
                  <p className="muted">No transactions loaded.</p>
                ) : (
                  <table className="tx-table">
                    <thead>
                      <tr>
                        <th>When</th>
                        <th>Type</th>
                        <th>Direction</th>
                        <th>Amount</th>
                        <th>IDs</th>
                      </tr>
                    </thead>
                    <tbody>
                      {transactions.map((tx) => (
                        <tr key={tx.id}>
                          <td>{formatIso(tx.createdAt)}</td>
                          <td>{tx.type}</td>
                          <td>{tx.direction}</td>
                          <td>{tx.amount} {tx.currency}</td>
                          <td>
                            <div className="mono">tx: {shortId(tx.id)}</div>
                            <div className="mono">related: {shortId(tx.relatedAccountId ?? undefined)}</div>
                            <div className="mono">ref: {shortId(tx.referenceTransactionId ?? undefined)}</div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}

                <div className="button-row">
                  <button
                    type="button"
                    onClick={() => void loadMoreTransactions()}
                    disabled={!nextCursor || busyKey !== null}
                  >
                    Load More
                  </button>
                </div>
              </Panel>

              <Panel title="Request / Response Trace" subtitle="Returned request IDs and payload summaries">
                {activity.length === 0 ? (
                  <p className="muted">No API calls recorded yet.</p>
                ) : (
                  <ul className="activity-list">
                    {activity.map((item) => (
                      <li key={item.id}>
                        <div className="activity-meta">
                          <span>{formatIso(item.at)}</span>
                          <span>{item.label}</span>
                          <span>{item.method} {item.path}</span>
                          <span>Status {item.status}</span>
                          <span className="mono">ReqID: {item.requestId ?? "-"}</span>
                        </div>
                        <pre className="payload small">{item.summary}</pre>
                      </li>
                    ))}
                  </ul>
                )}
              </Panel>

              <Panel title="Last API Result" subtitle="Detailed payload for latest operation">
                <pre className="payload">{lastPayload || "No operations yet."}</pre>
              </Panel>
            </div>
          </main>
        </>
      )}
    </div>
  );
}

export default App;
