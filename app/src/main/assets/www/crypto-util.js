"use strict";

const CRYPTO_VERSION = 2;

/**
 * 计算 SHA-256 哈希（Hex 字符串）
 */
async function sha256Hex(data) {
  const buf = new TextEncoder().encode(data);
  const hash = await crypto.subtle.digest("SHA-256", buf);
  return Array.from(new Uint8Array(hash))
    .map(b => b.toString(16).padStart(2, "0"))
    .join("");
}

/**
 * 从密码派生 AES-256 密钥（PBKDF2，10万次迭代）
 */
async function deriveKey(password, salt) {
  const enc = new TextEncoder();
  const keyMaterial = await crypto.subtle.importKey(
    "raw", enc.encode(password), { name: "PBKDF2" }, false, ["deriveKey"]
  );
  return crypto.subtle.deriveKey(
    { name: "PBKDF2", salt, iterations: 100000, hash: "SHA-256" },
    keyMaterial,
    { name: "AES-GCM", length: 256 },
    false,
    ["encrypt", "decrypt"]
  );
}

/**
 * AES-256-GCM 加密
 * 返回 { salt, iv, ciphertext } 的 base64 对象
 */
async function aesEncrypt(plaintext, password) {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const key = await deriveKey(password, salt);
  const enc = new TextEncoder();
  const ciphertext = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv },
    key,
    enc.encode(plaintext)
  );
  return {
    salt: btoa(String.fromCharCode(...salt)),
    iv: btoa(String.fromCharCode(...iv)),
    ciphertext: btoa(String.fromCharCode(...new Uint8Array(ciphertext)))
  };
}

/**
 * AES-256-GCM 解密
 */
async function aesDecrypt(payload, password) {
  const salt = Uint8Array.from(atob(payload.salt), c => c.charCodeAt(0));
  const iv = Uint8Array.from(atob(payload.iv), c => c.charCodeAt(0));
  const ciphertext = Uint8Array.from(atob(payload.ciphertext), c => c.charCodeAt(0));
  const key = await deriveKey(password, salt);
  const decrypted = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv },
    key,
    ciphertext
  );
  return new TextDecoder().decode(decrypted);
}

/**
 * 导出密码表（带可选校验和加密）
 * @param {string} base64Data - 原始密码表 base64 字符串
 * @param {boolean} enableHash - 是否启用 SHA-256 完整性校验
 * @param {boolean} enableEncrypt - 是否启用 AES-256 加密
 * @param {string} password - 加密密码（仅当 enableEncrypt 为 true 时）
 * @returns {object} 导出对象
 */
async function exportMimabiao(base64Data, enableHash, enableEncrypt, password) {
  const result = { version: CRYPTO_VERSION, data: base64Data };
  if (enableHash) {
    result.hash = await sha256Hex(base64Data);
  }
  if (enableEncrypt) {
    if (!password) throw new Error("加密密码不能为空");
    const encrypted = await aesEncrypt(base64Data, password);
    result.encrypted = true;
    result.payload = encrypted;
    delete result.data; // 明文不再存储
  } else {
    result.encrypted = false;
  }
  return result;
}

/**
 * 导入密码表（自动检测新旧格式并校验/解密）
 * @param {string} content - 文件内容
 * @param {string|null} password - 用户提供的解密密码（加密时必需）
 * @returns {Promise<{data: string, hashVerified: boolean, encrypted: boolean}>}
 */
async function importMimabiao(content, password) {
  content = content.trim();

  // 尝试新格式（JSON）
  let obj;
  try {
    obj = JSON.parse(content);
  } catch (e) {
    obj = null;
  }

  if (obj && obj.version === CRYPTO_VERSION) {
    let data = obj.data;
    let hashVerified = false;
    let encrypted = obj.encrypted || false;

    // 解密
    if (encrypted) {
      if (!password) throw new Error("此密码表已加密，请输入密码");
      if (!obj.payload) throw new Error("加密数据损坏");
      data = await aesDecrypt(obj.payload, password);
    }

    if (!data) throw new Error("密码表数据为空");

    // 完整性校验
    if (obj.hash) {
      const computed = await sha256Hex(data);
      if (computed !== obj.hash) {
        throw new Error("完整性校验失败：密码表已被篡改或损坏");
      }
      hashVerified = true;
    }

    return { data, hashVerified, encrypted };
  }

  // 旧格式：纯 base64 字符串（向后兼容）
  try {
    const decoded = atob(content);
    const arr = JSON.parse(decoded);
    if (Array.isArray(arr) && arr.length === 2048) {
      return { data: content, hashVerified: false, encrypted: false };
    }
  } catch (e) {
    // 不是旧格式
  }

  throw new Error("无法识别密码表格式");
}
