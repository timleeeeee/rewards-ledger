export function shortId(value: string | null | undefined): string {
  if (!value) {
    return "-";
  }

  if (value.length <= 12) {
    return value;
  }

  return `${value.slice(0, 6)}...${value.slice(-6)}`;
}

export function formatIso(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  return date.toLocaleString();
}

export function nextIdempotencyKey(prefix: string): string {
  return `${prefix}-${crypto.randomUUID()}`;
}

export function nextRequestId(): string {
  return crypto.randomUUID();
}
