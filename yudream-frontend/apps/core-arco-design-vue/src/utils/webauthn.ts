function base64UrlToArrayBuffer(value: string) {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(value.length / 4) * 4, '=')
  const binary = window.atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes.buffer
}

function arrayBufferToBase64Url(value: ArrayBuffer) {
  const bytes = new Uint8Array(value)
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return window.btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

function normalizeCreationOptions(publicKeyJson: string): CredentialCreationOptions {
  const parsed = JSON.parse(publicKeyJson)
  const publicKey = parsed.publicKey ?? parsed
  publicKey.challenge = base64UrlToArrayBuffer(publicKey.challenge)
  publicKey.user.id = base64UrlToArrayBuffer(publicKey.user.id)
  publicKey.excludeCredentials = publicKey.excludeCredentials?.map((item: PublicKeyCredentialDescriptor) => ({
    ...item,
    id: base64UrlToArrayBuffer(item.id as unknown as string),
  }))
  return { publicKey }
}

export async function createPasskeyRegistrationResponse(publicKeyJson: string) {
  if (!window.PublicKeyCredential || !navigator.credentials?.create) {
    throw new Error('当前浏览器不支持 Passkey')
  }
  const credential = await navigator.credentials.create(normalizeCreationOptions(publicKeyJson))
  if (!credential) {
    throw new Error('Passkey 创建已取消')
  }
  const publicKeyCredential = credential as PublicKeyCredential
  const response = publicKeyCredential.response as AuthenticatorAttestationResponse
  return JSON.stringify({
    id: publicKeyCredential.id,
    rawId: arrayBufferToBase64Url(publicKeyCredential.rawId),
    type: publicKeyCredential.type,
    authenticatorAttachment: publicKeyCredential.authenticatorAttachment,
    response: {
      clientDataJSON: arrayBufferToBase64Url(response.clientDataJSON),
      attestationObject: arrayBufferToBase64Url(response.attestationObject),
      transports: response.getTransports?.() ?? [],
    },
    clientExtensionResults: publicKeyCredential.getClientExtensionResults(),
  })
}
