import axios from 'axios'

interface PublicKeyResult {
  code: number
  data?: {
    enabled: boolean
    algorithm: string
    publicKey?: string
  }
}

interface EncryptionSession {
  encryptedKey: string
  key: CryptoKey
}

interface EncryptedPayload {
  encrypted?: boolean
  iv?: string
  data?: string
}

const backendBaseURL = (import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY) ? '/proxy/' : import.meta.env.VITE_APP_API_BASEURL
let publicKeyCache: { enabled: boolean, publicKey?: CryptoKey } | null = null

export function isEncryptablePayload(data: unknown) {
  return data != null && !(data instanceof FormData) && !(data instanceof Blob) && !(data instanceof ArrayBuffer)
}

export async function prepareApiEncryption(url?: string, data?: unknown) {
  if (url?.includes('api/system/security/encryption/public-key')) {
    return null
  }
  const publicKey = await loadPublicKey()
  if (!publicKey.enabled || !publicKey.publicKey) {
    return null
  }
  const session = await createSession(publicKey.publicKey)
  const headers: Record<string, string> = {
    'X-Api-Encrypted': 'true',
    'X-Api-Encrypted-Key': session.encryptedKey,
  }
  let body = data
  if (isEncryptablePayload(data)) {
    const encrypted = await encryptWithSession(session.key, JSON.stringify(data))
    headers['X-Api-Encrypted-Iv'] = encrypted.iv
    body = { data: encrypted.data }
  }
  return { headers, body, key: session.key }
}

export async function decryptApiResponse(responseData: unknown, sessionKey?: CryptoKey) {
  const payload = responseData as EncryptedPayload
  if (!sessionKey || !payload?.encrypted || !payload.iv || !payload.data) {
    return responseData
  }
  const plain = await decryptWithSession(sessionKey, payload.iv, payload.data)
  return JSON.parse(plain)
}

export function clearApiEncryptionCache() {
  publicKeyCache = null
}

async function loadPublicKey() {
  if (publicKeyCache) {
    return publicKeyCache
  }
  const res = await axios.get<PublicKeyResult>('api/system/security/encryption/public-key', {
    baseURL: backendBaseURL,
    headers: { 'Accept-Language': 'zh-CN' },
  })
  const data = res.data.data
  if (!data?.enabled || !data.publicKey) {
    publicKeyCache = { enabled: false }
    return publicKeyCache
  }
  publicKeyCache = {
    enabled: true,
    publicKey: await crypto.subtle.importKey(
      'spki',
      base64ToArrayBuffer(data.publicKey),
      { name: 'RSA-OAEP', hash: 'SHA-256' },
      false,
      ['encrypt'],
    ),
  }
  return publicKeyCache
}

async function createSession(publicKey: CryptoKey): Promise<EncryptionSession> {
  const key = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt', 'decrypt'])
  const rawKey = await crypto.subtle.exportKey('raw', key)
  const encryptedKey = await crypto.subtle.encrypt({ name: 'RSA-OAEP' }, publicKey, rawKey)
  return { key, encryptedKey: arrayBufferToBase64(encryptedKey) }
}

async function encryptWithSession(key: CryptoKey, plain: string) {
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const encrypted = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, new TextEncoder().encode(plain))
  return {
    iv: arrayBufferToBase64(iv),
    data: arrayBufferToBase64(encrypted),
  }
}

async function decryptWithSession(key: CryptoKey, iv: string, data: string) {
  const plain = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: new Uint8Array(base64ToArrayBuffer(iv)) },
    key,
    base64ToArrayBuffer(data),
  )
  return new TextDecoder().decode(plain)
}

function base64ToArrayBuffer(value: string) {
  const binary = atob(value)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes.buffer
}

function arrayBufferToBase64(value: ArrayBuffer | Uint8Array) {
  const bytes = value instanceof Uint8Array ? value : new Uint8Array(value)
  let binary = ''
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte)
  })
  return btoa(binary)
}
