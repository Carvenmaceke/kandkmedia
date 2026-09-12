import React, { useState, useMemo, useRef, useEffect } from "react";
import jsPDF from "jspdf";
import {
  Users, Banknote, CalendarDays, FileText, Bell, CheckCircle2, XCircle,
  Clock, ChevronRight, Building2, Search, Download, Eye, X, Send,
  UserCircle2, LayoutDashboard, ClipboardList, Settings as SettingsIcon, LogOut,
  ArrowRight, ArrowLeft, ShieldCheck, SlidersHorizontal, KeyRound, ArrowLeftRight,
  Lock, Mail, Phone as PhoneIcon, AlertCircle, PenLine, Trash2, Paperclip, FileCheck2, LifeBuoy, MapPin, Bot, RefreshCw,
} from "lucide-react";

/* ---------------------------------------------------------------------- */
/* DESIGN TOKENS                                                          */
/* ---------------------------------------------------------------------- */
const T = {
  navy: "#17110F",       // near-black chrome (was navy)
  navyDeep: "#0A0706",   // deepest black for gradients
  teal: "#D81F2C",       // K and K Media brand red — primary accent/actions
  tealLight: "#FBEAEA",  // light red tint for pills/backgrounds
  amber: "#B9762A",
  amberBg: "#FBF0E1",
  red: "#8C2A2E",        // deep maroon for danger/rejected — distinct from brand red
  redBg: "#FBEBE9",
  green: "#2F7A55",
  greenBg: "#E9F5EE",
  purple: "#5B4E9E",
  purpleBg: "#EFEDF8",
  bg: "#F5F4F3",
  surface: "#FFFFFF",
  text: "#1A1414",
  muted: "#6B5F5D",
  border: "#E7E1E0",
};

const sans = "'IBM Plex Sans', 'Helvetica Neue', Arial, sans-serif";
const mono = "'IBM Plex Mono', 'SF Mono', Consolas, monospace";
const DEFAULT_PASSWORD = "password123";

/* ---------------------------------------------------------------------- */
/* SEED DATA (mutable — signup/profile edits update these at runtime)     */
/* ---------------------------------------------------------------------- */
const COMPANY = {
  name: "K and K Media (Pty) Ltd",
  regNo: "",
  address: "526, 16th Road, Constantia Square Office Park, Randjespark, Midrand, Gauteng, South Africa",
  email: "sales@kandkmedia.co.za",
  phone: "+27 11 312 2206",
  website: "www.kandkmedia.co.za",
  logo: "https://www.kandkmedia.co.za/wp-content/uploads/2024/05/cropped-cropped-K-and-K-Media-logo-New-1.png",
};

const ALLOWED_EMAIL_DOMAIN = "kandkmedia.co.za";
const isCompanyEmail = (email) => (email || "").toLowerCase().trim().endsWith(`@${ALLOWED_EMAIL_DOMAIN}`);

// `let`, not `const` — App syncs this from React state each render (same
// pattern as EMPLOYEES/LEAVE_BALANCES) so HR's salary-structure edits and
// new levels are reflected everywhere that reads LEVELS.
let LEVELS = [
  { name: "Intern", default: 4000, min: 3500, max: 6000 },
  { name: "Junior", default: 12000, min: 8000, max: 15000 },
  { name: "Mid-Level", default: 20000, min: 15000, max: 25000 },
  { name: "Senior", default: 30000, min: 25000, max: 45000 },
  { name: "Manager", default: 42000, min: 38000, max: 55000 },
];

const DEPARTMENTS = ["Digital Media", "Creative Services", "Publications", "Events Management", "Sales", "HR", "Admin", "IT"];

const SUPPORT_EMAIL = "itsupport@kandkmedia.co.za";
// Set this once the real backend (see /backend in this repo) is deployed
// somewhere reachable, e.g. "https://api.kandkmedia.co.za". Left empty
// means "not connected yet" — the Support form will say so plainly rather
// than pretending to send.
const API_BASE_URL = "https://kandkmedia.onrender.com";
const SUPPORT_CATEGORIES = ["System Malfunction / Bug", "Payslip Issue", "Leave Application Issue", "Account / Access Issue", "Other"];
const OFFICE_ISSUE_TYPES = ["Hardware / Equipment", "Network / WiFi", "Printer / Scanner", "Workstation / Computer", "Other"];
const SUPPORT_PRIORITIES = ["Low", "Medium", "High", "Urgent"];
const OFFICES = ["Midrand", "Sandton"];
const TICKET_STATUSES = ["Open", "In Progress", "Resolved"];

// Sourced from K and K Media's internal "IT Operations Documentation"
// (S. Memela, IT Specialist), plus a few general quick-fix tips added on
// request. The document's Office Activation section ends with a
// third-party script that bypasses official Microsoft licensing — that
// step is intentionally left out.
//
// `must`: every word here has to appear (as a substring) for this entry to
// even qualify — keeps e.g. "outlook" answers from firing on a plain
// "my computer is frozen" with no app named. `any`: at least one of these
// has to appear too (skipped if empty). Matched in array order, so more
// specific/topic-named entries are listed before generic fallbacks.
const IT_FAQ = [
  { must: [], any: ["printer", "print", "scanner", "scan"], answer: "First check the printer is connected to the network and powered on, and that the paper is loaded/aligned correctly. Still not working? This is an Office Issue (Printer/Scanner) — log one with your office selected so IT support can look at it." },
  { must: ["outlook"], any: ["freeze", "frozen", "hang", "responding", "slow"], answer: "For Outlook freezing or not responding: try starting it in safe mode first (press Windows + R, type outlook.exe /safe, Enter). If that helps, disable add-ins one by one under File > Options > Add-ins. You can also try File > Account Settings > Data Files > Settings > Compact Now." },
  { must: ["outlook"], any: ["crash", "startup", "won't open", "wont open", "open"], answer: "For Outlook crashing on startup: try creating a new Outlook profile (Control Panel > Mail > Show Profiles > Add), or run a Quick Repair (Settings > Apps > Microsoft Office > Modify > Quick Repair)." },
  { must: ["outlook"], any: ["password", "credentials"], answer: "If Outlook keeps asking for your password: clear saved credentials via Control Panel > Credential Manager > Windows Credentials, then check whether 'Always prompt for logon credentials' is enabled under Account Settings > Security." },
  { must: ["email"], any: ["sending", "receiving", "send", "won't send", "wont send", "stuck"], answer: "Check your account settings under File > Account Settings > Email, clear your outbox, and reduce attachment sizes if they're large. Outlook's built-in Inbox Repair Tool (scanpst.exe) can also fix a corrupted PST file." },
  { must: ["calendar"], any: [], answer: "For calendar sync issues: try removing and re-adding the calendar, and double-check sync settings on both your phone and desktop." },
  { must: ["email"], any: ["set up", "setup", "new account", "add account", "imap", "new email"], answer: "To set up a new company email account in Outlook: Add Account > choose IMAP manually > Incoming server mail.kandkmedia.co.za, port 993 > Outgoing server mail.kandkmedia.co.za, port 465. If you're not sure of your password, log a Support ticket (Account/Access Issue)." },
  { must: ["teams"], any: ["connect", "load", "stuck", "disconnect", "laggy", "freeze", "freezing", "frozen"], answer: "For Teams connectivity problems: check your internet connection, temporarily disable VPN/firewall, and try clearing the Teams cache." },
  { must: ["teams"], any: ["login", "sign in", "signin", "credentials"], answer: "For Teams login errors: double-check your credentials, make sure your device's date/time is correct (a mismatch can break sign-in), and check whether multi-factor authentication is the issue." },
  { must: ["teams"], any: ["message", "sync", "chat"], answer: "If Teams isn't showing new messages: restart the app, clear its cache, or reinstall Teams if that doesn't help." },
  { must: ["teams"], any: ["microphone", "camera", "webcam", "audio", "video", "mic", "echo"], answer: "For Teams audio/video problems: check your hardware connections, confirm the app and your system both have permission to use the mic/camera, and run a test call to verify." },
  { must: ["teams"], any: ["notification", "alert"], answer: "If you're not getting Teams notifications: check the notification settings inside Teams itself, and also your system/OS notification preferences." },
  { must: ["teams"], any: ["file", "upload", "onedrive", "sharepoint"], answer: "For file access errors in Teams: check your permissions, confirm OneDrive/SharePoint is properly connected, and try refreshing before retrying." },
  { must: ["teams"], any: ["meeting", "recording"], answer: "For meeting problems (recordings not saving, chat unavailable): check your organization's meeting policies, storage limits, and recording permissions." },
  { must: [], any: ["3cx", "softphone", "soft-phone", "phone system", "extension"], answer: "The 3CX portal is at https://kandkmedia.3cx.co.za:5001/ — setting up a new extension (with the QR code for the mobile app) needs to be done by IT support, so please log a ticket rather than trying to self-configure it." },
  { must: [], any: ["slow", "freeze", "freezing", "laptop", "computer", "crash", "frozen", "hang"], answer: "For a general slow or frozen computer, the simplest first step is a full restart — that alone resolves a surprising number of issues. If it's still slow or freezing after a restart, log an Office Issue (Hardware/Equipment)." },
  { must: [], any: ["wifi", "wi-fi", "internet", "network", "connection", "no internet"], answer: "Try turning WiFi off and back on, and confirm you're connected to the correct office network. If a router restart is something you have access to, that's worth trying too. Still not working? This is an Office Issue (Network/WiFi) — log one with your office selected." },
  { must: [], any: ["password", "reset", "locked out", "can't log in", "cannot log in", "login"], answer: "For a locked account or forgotten password to a company system (not Outlook specifically — see the Outlook password question for that), log a Support ticket (Account/Access Issue) so IT support can reset it for you." },
  { must: [], any: ["vpn", "remote"], answer: "Confirm you're using the correct VPN credentials and try reconnecting. If it still fails, log a Support ticket so IT support can check your access." },
  { must: [], any: ["payslip", "salary", "pay"], answer: "For a payslip that looks wrong, use Support (Payslip Issue) rather than this chat — that routes it straight to the right place with your details attached." },
  { must: [], any: ["leave", "vacation", "annual leave", "sick leave"], answer: "For a problem with a leave application, use Support (Leave Application Issue) — that routes it straight to the right place with your details attached." },
];

const LEAVE_TYPES = [
  "Annual Leave", "Sick Leave", "Family Responsibility Leave",
  "Study Leave", "Unpaid Leave", "Maternity Leave", "Parental Leave",
];

// Leave types that require supporting proof (a medical certificate, exam
// timetable, etc.) before HR/a manager can responsibly decide the request.
const PROOF_REQUIRED_TYPES = ["Sick Leave", "Maternity Leave", "Parental Leave", "Family Responsibility Leave", "Study Leave"];
const MAX_PROOF_FILE_BYTES = 4 * 1024 * 1024; // 4MB

// Annual Leave, Sick Leave and Maternity Leave text below is taken directly
// from K and K Media's own appointment letter terms. Family Responsibility
// and Parental Leave reflect the standard entitlements under the Basic
// Conditions of Employment Act (BCEA) — actual terms can still vary by
// individual contract, so this is guidance, not a substitute for reading
// your own letter of appointment.
const LEAVE_POLICY = {
  "Annual Leave": "15 consecutive days on full pay per leave cycle (12 months from your start date). Taken at a time mutually agreed with your manager.",
  "Sick Leave": "Per BCEA Section 22: 30 days on full pay every 36-month cycle from your start date. In your first 6 months, it accrues at a rate of 1 day for every 26 days worked, rather than being available all at once.",
  "Family Responsibility Leave": "Standard BCEA entitlement: 3 days per annual cycle (after 4 months of service) for the birth, illness, or death of an immediate family member.",
  "Study Leave": "Not set by law — granted at management's discretion, typically for exams or approved courses. Attach your exam timetable or course confirmation.",
  "Unpaid Leave": "Time off without pay, for anything not covered by another leave type. Subject to your manager's approval.",
  "Maternity Leave": "4 months, unpaid. Must start no later than 1 month before your expected due date, and you're required to give your employer 1 month's written notice before it begins.",
  "Parental Leave": "Standard BCEA entitlement: 10 consecutive days, unpaid, for a parent who isn't taking maternity leave.",
};

// role: "admin" | "hr" | "manager" | "employee" | "it_support"
// `EMPLOYEES` and `LEAVE_BALANCES` are `let`, not `const` — the App component
// syncs them from React state each render, so every screen always reads the
// latest signed-up users / edited profiles without a big prop-drilling pass.
//
// Starts empty — this is a live system now, not a demo. The first account
// created via Sign Up becomes the first real employee; everyone else signs
// up the same way or is added by HR.
// Exactly one seeded account: the real system owner. Nobody can sign up
// as Master — it's the sole account with unrestricted access, including
// the exclusive ability to grant HR/Admin/IT Support access to others.
// This is intentionally the one exception to "no seed data" — it's a
// real account, not sample data, and without it nobody could log in to
// assign roles to anyone else.
let EMPLOYEES = [
  { id: "EMP-00001", name: "Carven Maceke", role: "master", level: null, position: "Jnr IT Specialist", dept: "IT", salary: 6500, manager: null, managerName: "Matuma Letsoalo (Executive Chairman)", employmentType: "12-month renewable contract", start: "2026-08-01", email: "carven.maceke@kandkmedia.co.za", phone: "0607950837", office: "Sandton", agreedToTerms: true, termsAgreedAt: new Date().toISOString() },
];

const ROLE_LABEL = { master: "Master", admin: "Admin", hr: "HR", manager: "Manager", employee: "Employee", it_support: "IT Support" };
const ROLE_TONE = { master: "red", admin: "purple", hr: "teal", manager: "amber", employee: "muted", it_support: "indigo" };

let LEAVE_BALANCES = {
  "EMP-00001": { "Annual Leave": 15, "Sick Leave": 0, "Family Responsibility Leave": 3 },
};

const INITIAL_LEAVE_REQUESTS = [];

const PAST_MONTHS = ["June 2026", "July 2026", "August 2026"];
const CURRENT_MONTH = "September 2026";
const STAGES = ["DRAFT", "REVIEWED", "APPROVED", "FINALIZED", "PUBLISHED", "SENT"];

function calcPayroll(emp, monthIndex) {
  const basic = emp.salary;
  const overtime = [750, 400, 0][monthIndex % 3];
  const bonus = monthIndex === 2 ? Math.round(basic * 0.03) : 0;
  const housing = emp.level === "Manager" || emp.level === "Senior" ? 2000 : 0;
  const transport = emp.level === "Intern" ? 0 : 1000;
  const gross = basic + overtime + bonus + housing + transport;
  const paye = Math.round(gross * 0.15);
  const uif = Math.round(Math.min(gross, 17712) * 0.01);
  const totalDeductions = paye + uif;
  const net = gross - totalDeductions;
  return { basic, overtime, bonus, housing, transport, gross, paye, uif, totalDeductions, net };
}

function buildHistory(seedEmployees) {
  const history = {};
  seedEmployees.forEach((emp) => {
    history[emp.id] = PAST_MONTHS.map((m, i) => ({ month: m, figures: calcPayroll(emp, i) }));
  });
  return history;
}

const rand = (seed) => { const x = Math.sin(seed) * 10000; return x - Math.floor(x); };
function empById(id) { return EMPLOYEES.find((e) => e.id === id); }
function nextEmployeeId() {
  if (EMPLOYEES.length === 0) return "EMP-00001";
  const max = Math.max(...EMPLOYEES.map((e) => parseInt(e.id.split("-")[1], 10)));
  return `EMP-${String(max + 1).padStart(5, "0")}`;
}

/* ---------------------------------------------------------------------- */
/* SMALL UI PRIMITIVES                                                    */
/* ---------------------------------------------------------------------- */
const money = (n) => `R${n.toLocaleString("en-ZA")}`;

function Pill({ tone = "muted", children }) {
  const map = {
    muted: { bg: "#EEF0F3", fg: T.muted }, green: { bg: T.greenBg, fg: T.green },
    amber: { bg: T.amberBg, fg: T.amber }, red: { bg: T.redBg, fg: T.red },
    teal: { bg: T.tealLight, fg: T.teal }, purple: { bg: T.purpleBg, fg: T.purple },
    indigo: { bg: "#EBEAFB", fg: "#4F46C4" },
  }[tone];
  return <span style={{ background: map.bg, color: map.fg, fontSize: 12, fontWeight: 600, padding: "3px 9px", borderRadius: 4, whiteSpace: "nowrap" }}>{children}</span>;
}
function RolePill({ role }) { return <Pill tone={ROLE_TONE[role]}>{ROLE_LABEL[role]}</Pill>; }
function StatusPill({ status }) {
  const tone = status === "Approved" ? "green" : status === "Rejected" ? "red" : "amber";
  return <Pill tone={tone}>{status}</Pill>;
}
function Card({ children, style, ...rest }) {
  return <div style={{ background: T.surface, border: `1px solid ${T.border}`, borderRadius: 8, ...style }} {...rest}>{children}</div>;
}
function SectionTitle({ children, sub }) {
  return (
    <div style={{ marginBottom: 16 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
        <div style={{ width: 4, height: 18, background: T.teal, borderRadius: 2 }} />
        <h2 style={{ margin: 0, fontSize: 18, fontWeight: 650, color: T.text }}>{children}</h2>
      </div>
      {sub && <div style={{ marginLeft: 14, marginTop: 4, fontSize: 13, color: T.muted }}>{sub}</div>}
    </div>
  );
}
function StatCard({ icon: Icon, label, value, tone, title }) {
  const c = tone || T.navy;
  return (
    <Card style={{ padding: "16px 18px", flex: 1, minWidth: 150 }} title={title}>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 10 }}>
        <div style={{ width: 30, height: 30, borderRadius: 6, background: T.tealLight, display: "flex", alignItems: "center", justifyContent: "center" }}><Icon size={16} color={T.teal} /></div>
        <span style={{ fontSize: 12.5, color: T.muted, fontWeight: 600 }}>{label}</span>
      </div>
      <div style={{ fontFamily: mono, fontSize: 26, fontWeight: 600, color: c }}>{value}</div>
      {title && <div style={{ fontSize: 10.5, color: T.muted, marginTop: 6, lineHeight: 1.4 }}>{title}</div>}
    </Card>
  );
}
function Button({ children, onClick, variant = "primary", small, disabled, icon: Icon, type = "button", full }) {
  const styles = {
    primary: { bg: T.navy, fg: "#fff", border: T.navy }, teal: { bg: T.teal, fg: "#fff", border: T.teal },
    ghost: { bg: "transparent", fg: T.navy, border: T.border }, danger: { bg: "transparent", fg: T.red, border: T.red },
    success: { bg: "transparent", fg: T.green, border: T.green },
  }[variant];
  return (
    <button type={type} onClick={onClick} disabled={disabled} style={{
      display: "inline-flex", alignItems: "center", justifyContent: "center", gap: 6,
      background: styles.bg, color: styles.fg, border: `1px solid ${styles.border}`,
      borderRadius: 6, padding: small ? "5px 10px" : "9px 14px", width: full ? "100%" : "auto",
      fontSize: small ? 12.5 : 13.5, fontWeight: 600, cursor: disabled ? "not-allowed" : "pointer",
      opacity: disabled ? 0.45 : 1, fontFamily: sans,
    }}>{Icon && <Icon size={small ? 13 : 15} />}{children}</button>
  );
}
function Field({ label, children }) {
  return (
    <div style={{ marginBottom: 14 }}>
      <label style={{ fontSize: 12, fontWeight: 700, color: T.muted }}>{label}</label>
      <div style={{ marginTop: 5 }}>{children}</div>
    </div>
  );
}
const inputStyle = { width: "100%", padding: "9px 10px", borderRadius: 6, border: `1px solid ${T.border}`, fontSize: 13.5, fontFamily: sans, boxSizing: "border-box" };

/* ---------------------------------------------------------------------- */
/* SIGNATURE PAD — draw-to-sign, captured as a PNG data URL               */
/* ---------------------------------------------------------------------- */
function SignaturePad({ value, onChange, height = 130 }) {
  const canvasRef = useRef(null);
  const drawing = useRef(false);
  const lastPos = useRef(null);
  const hasStroke = useRef(false);

  useEffect(() => {
    const canvas = canvasRef.current;
    const ctx = canvas.getContext("2d");
    ctx.lineWidth = 2.2;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.strokeStyle = T.text;
    if (value) {
      const img = new Image();
      img.onload = () => ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      img.src = value;
      hasStroke.current = true;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const getPos = (e) => {
    const rect = canvasRef.current.getBoundingClientRect();
    const clientX = e.touches ? e.touches[0].clientX : e.clientX;
    const clientY = e.touches ? e.touches[0].clientY : e.clientY;
    const scaleX = canvasRef.current.width / rect.width;
    const scaleY = canvasRef.current.height / rect.height;
    return { x: (clientX - rect.left) * scaleX, y: (clientY - rect.top) * scaleY };
  };

  const start = (e) => {
    e.preventDefault();
    drawing.current = true;
    lastPos.current = getPos(e);
  };
  const move = (e) => {
    if (!drawing.current) return;
    e.preventDefault();
    const ctx = canvasRef.current.getContext("2d");
    const pos = getPos(e);
    ctx.beginPath();
    ctx.moveTo(lastPos.current.x, lastPos.current.y);
    ctx.lineTo(pos.x, pos.y);
    ctx.stroke();
    lastPos.current = pos;
    hasStroke.current = true;
  };
  const end = () => {
    if (!drawing.current) return;
    drawing.current = false;
    onChange(hasStroke.current ? canvasRef.current.toDataURL("image/png") : null);
  };
  const clear = () => {
    const canvas = canvasRef.current;
    canvas.getContext("2d").clearRect(0, 0, canvas.width, canvas.height);
    hasStroke.current = false;
    onChange(null);
  };

  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 6 }}>
        <PenLine size={13} color={T.muted} />
        <span style={{ fontSize: 12, fontWeight: 700, color: T.muted }}>Signature</span>
      </div>
      <canvas
        ref={canvasRef}
        width={500}
        height={height * 2}
        style={{ width: "100%", height, border: `1px solid ${T.border}`, borderRadius: 6, background: "#fff", touchAction: "none", cursor: "crosshair", display: "block" }}
        onMouseDown={start} onMouseMove={move} onMouseUp={end} onMouseLeave={end}
        onTouchStart={start} onTouchMove={move} onTouchEnd={end}
      />
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 6 }}>
        <span style={{ fontSize: 11, color: T.muted }}>Draw your signature above</span>
        <button type="button" onClick={clear} style={{ display: "flex", alignItems: "center", gap: 4, background: "none", border: "none", color: T.muted, fontSize: 11.5, cursor: "pointer", fontWeight: 600 }}>
          <Trash2 size={12} /> Clear
        </button>
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------------- */
/* PROOF-OF-LEAVE FILE ATTACHMENT                                         */
/* ---------------------------------------------------------------------- */
function dataUrlToBlob(dataUrl) {
  const [header, base64] = dataUrl.split(",");
  const mimeMatch = header.match(/:(.*?);/);
  const mime = mimeMatch ? mimeMatch[1] : "application/octet-stream";
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return new Blob([bytes], { type: mime });
}

function viewProofDocument(file) {
  if (!file || !file.dataUrl) return;
  try {
    const blob = dataUrlToBlob(file.dataUrl);
    const url = URL.createObjectURL(blob);
    window.open(url, "_blank");
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  } catch (e) {
    console.error("Could not open proof document:", e);
  }
}

const fileSizeLabel = (bytes) => bytes < 1024 * 1024 ? `${Math.round(bytes / 1024)}KB` : `${(bytes / (1024 * 1024)).toFixed(1)}MB`;

function ProofUpload({ value, onChange, required }) {
  const inputRef = useRef(null);
  const [error, setError] = useState("");

  const handleFile = (file) => {
    if (!file) return;
    const okType = file.type === "application/pdf" || file.type.startsWith("image/");
    if (!okType) { setError("Only PDF or image files are accepted."); return; }
    if (file.size > MAX_PROOF_FILE_BYTES) { setError(`File is too large (max ${fileSizeLabel(MAX_PROOF_FILE_BYTES)}).`); return; }
    setError("");
    const reader = new FileReader();
    reader.onload = () => onChange({ name: file.name, type: file.type, size: file.size, dataUrl: reader.result });
    reader.readAsDataURL(file);
  };

  return (
    <div>
      <label style={{ fontSize: 12, fontWeight: 700, color: T.muted }}>
        Proof of Leave {required && <span style={{ color: T.red }}>*</span>}
      </label>
      {!value ? (
        <div onClick={() => inputRef.current.click()} style={{
          marginTop: 5, border: `1.5px dashed ${T.border}`, borderRadius: 8, padding: "16px 14px",
          textAlign: "center", cursor: "pointer", background: T.bg,
        }}>
          <Paperclip size={16} color={T.muted} style={{ marginBottom: 4 }} />
          <div style={{ fontSize: 12.5, color: T.muted }}>Click to attach a PDF or image (max {fileSizeLabel(MAX_PROOF_FILE_BYTES)})</div>
        </div>
      ) : (
        <div style={{ marginTop: 5, display: "flex", alignItems: "center", justifyContent: "space-between", border: `1px solid ${T.border}`, borderRadius: 8, padding: "9px 12px", background: T.tealLight }}>
          <button type="button" onClick={() => viewProofDocument(value)} style={{ display: "flex", alignItems: "center", gap: 8, minWidth: 0, background: "none", border: "none", cursor: "pointer", textAlign: "left" }}>
            <FileCheck2 size={15} color={T.teal} style={{ flexShrink: 0 }} />
            <span style={{ fontSize: 12.5, fontWeight: 600, color: T.text, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{value.name}</span>
            <span style={{ fontSize: 11, color: T.muted, flexShrink: 0 }}>({fileSizeLabel(value.size)})</span>
          </button>
          <button type="button" onClick={() => onChange(null)} style={{ background: "none", border: "none", cursor: "pointer", color: T.muted, flexShrink: 0 }}><X size={14} /></button>
        </div>
      )}
      <input ref={inputRef} type="file" accept="application/pdf,image/*" style={{ display: "none" }}
        onChange={(e) => handleFile(e.target.files[0])} />
      {error && <div style={{ fontSize: 11, color: T.red, marginTop: 4 }}>{error}</div>}
    </div>
  );
}


/* PDF GENERATION — builds an actual downloadable payslip PDF client-side */
/* ---------------------------------------------------------------------- */
function downloadPayslipPdf(emp, month, figures) {
  const doc = new jsPDF({ unit: "pt", format: "a4" });
  const pageWidth = doc.internal.pageSize.getWidth();
  const marginX = 42;
  let y;

  // Header band — matches the on-screen payslip's dark header with red accent
  doc.setFillColor(23, 17, 15);
  doc.rect(0, 0, pageWidth, 108, "F");
  doc.setFillColor(216, 31, 44);
  doc.rect(marginX, 26, 40, 3, "F");

  doc.setTextColor(255, 255, 255);
  doc.setFont("helvetica", "bold");
  doc.setFontSize(15);
  doc.text(COMPANY.name, marginX, 50);

  doc.setFont("helvetica", "normal");
  doc.setFontSize(8.5);
  doc.setTextColor(201, 191, 188);
  const addressLines = doc.splitTextToSize(COMPANY.address, 300);
  doc.text(addressLines, marginX, 64);
  let afterAddressY = 64 + (addressLines.length - 1) * 10;
  if (COMPANY.regNo) {
    afterAddressY += 12;
    doc.text(`Reg No: ${COMPANY.regNo}`, marginX, afterAddressY);
  }

  doc.setTextColor(255, 255, 255);
  doc.setFont("helvetica", "bold");
  doc.setFontSize(11);
  doc.text(emp.name, pageWidth - marginX, 50, { align: "right" });
  doc.setFont("helvetica", "normal");
  doc.setFontSize(8.5);
  doc.setTextColor(201, 191, 188);
  doc.text(`${emp.id} \u00b7 ${emp.position}`, pageWidth - marginX, 64, { align: "right" });
  doc.text(`Pay Period: ${month}`, pageWidth - marginX, 78, { align: "right" });

  const row = (label, value, bold) => {
    doc.setFont("helvetica", bold ? "bold" : "normal");
    doc.setFontSize(10.5);
    doc.setTextColor(26, 20, 20);
    doc.text(label, marginX, y);
    doc.text(money(value), pageWidth - marginX, y, { align: "right" });
    doc.setDrawColor(231, 225, 224);
    doc.line(marginX, y + 5, pageWidth - marginX, y + 5);
    y += 19;
  };

  const sectionHeader = (label) => {
    doc.setFont("helvetica", "bold");
    doc.setFontSize(10);
    doc.setTextColor(216, 31, 44);
    doc.text(label, marginX, y);
    y += 16;
  };

  y = 140;
  sectionHeader("EARNINGS");
  row("Basic Salary", figures.basic);
  if (figures.housing > 0) row("Housing Allowance", figures.housing);
  if (figures.transport > 0) row("Transport Allowance", figures.transport);
  if (figures.overtime > 0) row("Overtime", figures.overtime);
  if (figures.bonus > 0) row("Bonus", figures.bonus);
  row("Gross Earnings", figures.gross, true);

  y += 12;
  sectionHeader("DEDUCTIONS");
  row("PAYE", -figures.paye);
  row("UIF", -figures.uif);
  row("Total Deductions", -figures.totalDeductions, true);

  y += 16;
  doc.setFillColor(251, 234, 234);
  doc.roundedRect(marginX, y - 15, pageWidth - marginX * 2, 34, 4, 4, "F");
  doc.setFont("helvetica", "bold");
  doc.setFontSize(12);
  doc.setTextColor(23, 17, 15);
  doc.text("Net Pay", marginX + 12, y + 7);
  doc.text(money(figures.net), pageWidth - marginX - 12, y + 7, { align: "right" });

  y += 48;
  doc.setFont("helvetica", "normal");
  doc.setFontSize(7.5);
  doc.setTextColor(120, 110, 108);
  doc.text("Figures are illustrative dummy data, not real tax calculations.", marginX, y);

  const filename = `${month.replace(" ", "-")}-${emp.id}.pdf`;
  triggerPdfDownload(doc, filename);
}

function triggerPdfDownload(doc, filename) {
  try {
    const blob = doc.output("blob");
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setTimeout(() => URL.revokeObjectURL(url), 4000);
  } catch (err) {
    // Some embedded/sandboxed browser contexts block a programmatic download
    // click. Fall back to opening the PDF in a new tab so the person can
    // still save it manually from there.
    console.error("PDF download failed, opening in a new tab instead:", err);
    window.open(doc.output("bloburl"), "_blank");
  }
}

/* ---------------------------------------------------------------------- */
/* LEAVE DECISION LETTER — signed approval/decline document               */
/* ---------------------------------------------------------------------- */
function downloadLeaveLetter(request, applicant) {
  const doc = new jsPDF({ unit: "pt", format: "a4" });
  const pageWidth = doc.internal.pageSize.getWidth();
  const marginX = 42;
  let y;

  const approved = request.status === "Approved";

  // Header band
  doc.setFillColor(23, 17, 15);
  doc.rect(0, 0, pageWidth, 92, "F");
  doc.setFillColor(216, 31, 44);
  doc.rect(marginX, 26, 40, 3, "F");
  doc.setTextColor(255, 255, 255);
  doc.setFont("helvetica", "bold");
  doc.setFontSize(15);
  doc.text(COMPANY.name, marginX, 50);
  doc.setFont("helvetica", "normal");
  doc.setFontSize(8.5);
  doc.setTextColor(201, 191, 188);
  const addrLines = doc.splitTextToSize(COMPANY.address, 320);
  doc.text(addrLines, marginX, 64);

  doc.setFont("helvetica", "bold");
  doc.setFontSize(11);
  doc.setTextColor(255, 255, 255);
  const title = "LEAVE REQUEST " + (approved ? "APPROVAL" : "DECLINE") + " LETTER";
  doc.text(title, pageWidth - marginX - doc.getTextWidth(title), 50);

  y = 130;
  doc.setTextColor(20, 20, 20);
  doc.setFont("helvetica", "normal");
  doc.setFontSize(10.5);
  doc.text(`Date: ${new Date().toLocaleDateString("en-ZA", { day: "numeric", month: "long", year: "numeric" })}`, marginX, y);
  y += 26;

  doc.setFont("helvetica", "bold");
  doc.text(`Dear ${applicant.name},`, marginX, y);
  y += 22;

  doc.setFont("helvetica", "normal");
  const bodyText = approved
    ? `This letter confirms that your ${request.type} request has been APPROVED.`
    : `This letter confirms that your ${request.type} request has been DECLINED.`;
  const bodyLines = doc.splitTextToSize(bodyText, pageWidth - marginX * 2);
  doc.text(bodyLines, marginX, y);
  y += bodyLines.length * 14 + 16;

  const detailRow = (label, value) => {
    doc.setFont("helvetica", "bold");
    doc.setFontSize(9.5);
    doc.setTextColor(90, 82, 80);
    doc.text(label, marginX, y);
    doc.setFont("helvetica", "normal");
    doc.setTextColor(20, 20, 20);
    doc.text(String(value), marginX + 140, y);
    y += 17;
  };

  detailRow("Employee:", `${applicant.name} (${applicant.id})`);
  detailRow("Leave Type:", request.type);
  detailRow("Dates:", `${request.start} to ${request.end}`);
  detailRow("Days Requested:", request.days);
  detailRow("Applicant's Reason:", request.reason || "—");
  if (request.proofFileName) detailRow("Supporting Document:", request.proofFileName);

  y += 6;
  doc.setFillColor(approved ? 233 : 251, approved ? 245 : 235, approved ? 238 : 233);
  doc.roundedRect(marginX, y - 14, pageWidth - marginX * 2, 26, 4, 4, "F");
  doc.setFont("helvetica", "bold");
  doc.setFontSize(11);
  doc.setTextColor(approved ? 47 : 140, approved ? 122 : 42, approved ? 85 : 46);
  doc.text(`Status: ${request.status}`, marginX + 12, y + 4);
  y += 40;

  if (!approved && request.decisionReason) {
    doc.setFont("helvetica", "bold");
    doc.setFontSize(9.5);
    doc.setTextColor(90, 82, 80);
    doc.text("Reason for decline:", marginX, y);
    y += 15;
    doc.setFont("helvetica", "normal");
    doc.setTextColor(20, 20, 20);
    const reasonLines = doc.splitTextToSize(request.decisionReason, pageWidth - marginX * 2);
    doc.text(reasonLines, marginX, y);
    y += reasonLines.length * 14 + 10;
  }

  y += 30;

  // Signature blocks — employee (applicant) left, decider right
  const sigBoxWidth = (pageWidth - marginX * 2 - 30) / 2;
  const sigImgHeight = 50;

  const drawSignatureBlock = (x, label, signatureDataUrl, nameLine, dateLine) => {
    if (signatureDataUrl) {
      try {
        doc.addImage(signatureDataUrl, "PNG", x, y, sigBoxWidth, sigImgHeight);
      } catch (e) {
        // Corrupt/unsupported image data — fall through to just the line and label.
      }
    }
    doc.setDrawColor(140, 130, 128);
    doc.setLineWidth(0.75);
    doc.line(x, y + sigImgHeight + 6, x + sigBoxWidth, y + sigImgHeight + 6);
    doc.setFont("helvetica", "bold");
    doc.setFontSize(9);
    doc.setTextColor(20, 20, 20);
    doc.text(nameLine, x, y + sigImgHeight + 20);
    doc.setFont("helvetica", "normal");
    doc.setFontSize(8);
    doc.setTextColor(120, 110, 108);
    doc.text(label, x, y + sigImgHeight + 32);
    if (dateLine) doc.text(dateLine, x, y + sigImgHeight + 44);
  };

  drawSignatureBlock(marginX, "Applicant", request.employeeSignature, applicant.name,
    request.employeeSignedAt ? `Signed: ${new Date(request.employeeSignedAt).toLocaleDateString("en-ZA")}` : "");
  drawSignatureBlock(marginX + sigBoxWidth + 30, `${approved ? "Approved" : "Declined"} by`, request.deciderSignature,
    request.deciderName || "—", request.deciderSignedAt ? `Signed: ${new Date(request.deciderSignedAt).toLocaleDateString("en-ZA")}` : "");

  y += sigImgHeight + 70;
  doc.setFont("helvetica", "normal");
  doc.setFontSize(7.5);
  doc.setTextColor(140, 130, 128);
  doc.text("This document was electronically generated and signed within the K and K Media Payroll System.", marginX, y);

  const filename = `Leave-${request.status}-${request.id}-${applicant.id}.pdf`;
  triggerPdfDownload(doc, filename);
}

/* ---------------------------------------------------------------------- */
/* ONBOARDING DOCUMENT — HR-downloadable record of everything an employee */
/* entered at signup, plus the signature they gave consenting to it       */
/* ---------------------------------------------------------------------- */
function downloadOnboardingDocument(employee) {
  const doc = new jsPDF({ unit: "pt", format: "a4" });
  const pageWidth = doc.internal.pageSize.getWidth();
  const pageHeight = doc.internal.pageSize.getHeight();
  const marginX = 42;
  let y;

  const newPageIfNeeded = (needed) => {
    if (y - needed < 50) {
      doc.addPage();
      y = pageHeight - 50;
    }
  };

  // Header
  doc.setFillColor(23, 17, 15);
  doc.rect(0, 0, pageWidth, 90, "F");
  doc.setFillColor(216, 31, 44);
  doc.rect(marginX, 26, 40, 3, "F");
  doc.setTextColor(255, 255, 255);
  doc.setFont("helvetica", "bold");
  doc.setFontSize(15);
  doc.text(COMPANY.name, marginX, 50);
  doc.setFont("helvetica", "normal");
  doc.setFontSize(8.5);
  doc.setTextColor(201, 191, 188);
  doc.text(COMPANY.address, marginX, 64);
  const title = "EMPLOYEE ONBOARDING RECORD";
  doc.setFont("helvetica", "bold");
  doc.setFontSize(11);
  doc.setTextColor(255, 255, 255);
  doc.text(title, pageWidth - marginX - doc.getTextWidth(title), 50);

  y = 128;
  doc.setFont("helvetica", "bold");
  doc.setFontSize(13);
  doc.setTextColor(20, 20, 20);
  doc.text(employee.name, marginX, y);
  doc.setFont("helvetica", "normal");
  doc.setFontSize(9.5);
  doc.setTextColor(90, 82, 80);
  y += 16;
  doc.text(`${employee.id} · ${employee.position || "—"} · ${employee.dept || "—"} · ${employee.office || "—"}`, marginX, y);
  y += 26;

  const row = (label, value) => {
    newPageIfNeeded(30);
    doc.setFont("helvetica", "bold");
    doc.setFontSize(9);
    doc.setTextColor(90, 82, 80);
    doc.text(label, marginX, y);
    doc.setFont("helvetica", "normal");
    doc.setTextColor(20, 20, 20);
    const lines = doc.splitTextToSize(String(value || "—"), pageWidth - marginX * 2 - 150);
    doc.text(lines, marginX + 150, y);
    y += Math.max(15, lines.length * 12 + 3);
  };

  const sectionHeader = (label) => {
    newPageIfNeeded(30);
    y += 6;
    doc.setFillColor(251, 234, 234);
    doc.rect(marginX, y - 12, pageWidth - marginX * 2, 18, "F");
    doc.setFont("helvetica", "bold");
    doc.setFontSize(9.5);
    doc.setTextColor(216, 31, 44);
    doc.text(label.toUpperCase(), marginX + 6, y);
    y += 20;
  };

  PERSONAL_INFO_GROUPS.forEach((group) => {
    sectionHeader(group.title);
    group.fields.forEach(([key, label]) => row(label, employee[key]));
  });

  sectionHeader("Employment");
  row("Department", employee.dept);
  row("Position", employee.position);
  row("Office", employee.office);
  row("Start Date", employee.start);

  newPageIfNeeded(140);
  y += 20;
  doc.setFont("helvetica", "normal");
  doc.setFontSize(9);
  doc.setTextColor(90, 82, 80);
  const declaration = "I declare that the information provided in this document is true and correct, and I consent to K and K Media (Pty) Ltd processing this personal information for payroll and HR administration purposes.";
  const declLines = doc.splitTextToSize(declaration, pageWidth - marginX * 2);
  doc.text(declLines, marginX, y);
  y += declLines.length * 13 + 24;

  const sigWidth = 200;
  const sigHeight = 50;
  if (employee.onboardingSignature) {
    try {
      doc.addImage(employee.onboardingSignature, "PNG", marginX, y, sigWidth, sigHeight);
    } catch (e) {
      // corrupt/unsupported signature image — fall through to just the line
    }
  }
  doc.setDrawColor(140, 130, 128);
  doc.setLineWidth(0.75);
  doc.line(marginX, y + sigHeight + 6, marginX + sigWidth, y + sigHeight + 6);
  doc.setFont("helvetica", "bold");
  doc.setFontSize(9);
  doc.setTextColor(20, 20, 20);
  doc.text(employee.name, marginX, y + sigHeight + 20);
  doc.setFont("helvetica", "normal");
  doc.setFontSize(8);
  doc.setTextColor(120, 110, 108);
  doc.text(employee.termsAgreedAt ? `Signed: ${new Date(employee.termsAgreedAt).toLocaleDateString("en-ZA")}` : "Not yet signed", marginX, y + sigHeight + 32);

  y += sigHeight + 60;
  doc.setFont("helvetica", "normal");
  doc.setFontSize(7.5);
  doc.setTextColor(140, 130, 128);
  doc.text("This document was generated from information the employee entered and signed at sign-up.", marginX, y);

  triggerPdfDownload(doc, `Onboarding-${employee.id}-${employee.name.replace(/\s+/g, "-")}.pdf`);
}

/* ---------------------------------------------------------------------- */
/* PAYSLIP DOCUMENT                                                       */
/* ---------------------------------------------------------------------- */
function Payslip({ emp, month, figures, onClose }) {
  const row = (label, value, strong) => (
    <div style={{ display: "flex", justifyContent: "space-between", padding: "7px 0", borderBottom: `1px solid ${T.border}`, fontSize: 13.5, fontWeight: strong ? 700 : 400, color: strong ? T.text : "#3A4150" }}>
      <span>{label}</span><span style={{ fontFamily: mono }}>{money(value)}</span>
    </div>
  );
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(20,10,9,0.6)", zIndex: 60, display: "flex", alignItems: "flex-start", justifyContent: "center", padding: "40px 16px", overflowY: "auto" }} onClick={onClose}>
      <div style={{ background: T.surface, width: 520, maxWidth: "100%", borderRadius: 10, overflow: "hidden", boxShadow: "0 20px 60px rgba(0,0,0,.35)" }} onClick={(e) => e.stopPropagation()}>
        <div style={{ background: `linear-gradient(135deg, ${T.navy}, ${T.navyDeep})`, padding: "22px 26px" }}>
          <div style={{ height: 3, width: 46, background: T.teal, borderRadius: 2, marginBottom: 12 }} />
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
            <div>
              <div style={{ color: "#fff", fontSize: 17, fontWeight: 700 }}>{COMPANY.name}</div>
              <div style={{ color: "#C9BFBC", fontSize: 11.5, marginTop: 3, lineHeight: 1.5 }}>{COMPANY.address}{COMPANY.regNo && <><br />Reg No: {COMPANY.regNo}</>}</div>
            </div>
            <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} color="#fff" /></button>
          </div>
          <div style={{ marginTop: 16, paddingTop: 14, borderTop: "1px solid rgba(255,255,255,0.15)", display: "flex", justifyContent: "space-between", color: "#fff" }}>
            <div>
              <div style={{ fontSize: 14, fontWeight: 650 }}>{emp.name}</div>
              <div style={{ fontSize: 11.5, color: "#C9BFBC", fontFamily: mono }}>{emp.id} · {emp.position}</div>
            </div>
            <div style={{ textAlign: "right" }}>
              <div style={{ fontSize: 11.5, color: "#C9BFBC" }}>Pay Period</div>
              <div style={{ fontSize: 13.5, fontWeight: 600 }}>{month}</div>
            </div>
          </div>
        </div>
        <div style={{ padding: "20px 26px 26px" }}>
          <div style={{ fontSize: 11.5, fontWeight: 700, color: T.teal, marginBottom: 4 }}>EARNINGS</div>
          {row("Basic Salary", figures.basic)}
          {figures.housing > 0 && row("Housing Allowance", figures.housing)}
          {figures.transport > 0 && row("Transport Allowance", figures.transport)}
          {figures.overtime > 0 && row("Overtime", figures.overtime)}
          {figures.bonus > 0 && row("Bonus", figures.bonus)}
          {row("Gross Earnings", figures.gross, true)}
          <div style={{ fontSize: 11.5, fontWeight: 700, color: T.teal, margin: "18px 0 4px" }}>DEDUCTIONS</div>
          {row("PAYE", -figures.paye)}
          {row("UIF", -figures.uif)}
          {row("Total Deductions", -figures.totalDeductions, true)}
          <div style={{ marginTop: 18, background: T.tealLight, borderRadius: 8, padding: "14px 16px", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ fontSize: 13.5, fontWeight: 700, color: T.navy }}>Net Pay</span>
            <span style={{ fontFamily: mono, fontSize: 20, fontWeight: 700, color: T.navy }}>{money(figures.net)}</span>
          </div>
          <div style={{ marginTop: 18, display: "flex", gap: 8 }}>
            <Button variant="teal" icon={Download} small onClick={() => downloadPayslipPdf(emp, month, figures)}>Download PDF</Button>
            <Button variant="ghost" icon={Send} small>Resend Email</Button>
          </div>
          <div style={{ marginTop: 10, fontSize: 10.5, color: T.muted }}>Filename: {month.replace(" ", "-")}-{emp.id}.pdf — figures are illustrative dummy data, not real tax calculations.</div>
        </div>
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------------- */
/* DECISION MODAL — decider signs off on approving/declining leave        */
/* ---------------------------------------------------------------------- */
function DecisionModal({ target, decider, onClose, onConfirm }) {
  const [signature, setSignature] = useState(null);
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");

  if (!target) return null;
  const { request, intent } = target;
  const approve = intent === "approve";

  const confirm = () => {
    if (!signature) { setError("Please sign before confirming."); return; }
    if (!approve && !reason.trim()) { setError("Please state a reason for declining."); return; }
    onConfirm(request.id, approve, signature, approve ? null : reason.trim());
  };

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(20,10,9,0.5)", zIndex: 55, display: "flex", alignItems: "center", justifyContent: "center", padding: 16 }} onClick={onClose}>
      <div style={{ background: T.surface, width: 440, maxWidth: "100%", borderRadius: 10, padding: 26, boxShadow: "0 20px 60px rgba(0,0,0,.35)" }} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 4 }}>
          <div>
            <div style={{ fontSize: 16, fontWeight: 700 }}>{approve ? "Approve" : "Decline"} leave request</div>
            <div style={{ fontSize: 12.5, color: T.muted, marginTop: 2 }}>{empById(request.emp)?.name} · {request.type} · {request.start} to {request.end}</div>
          </div>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} /></button>
        </div>

        {request.proofFileDataUrl ? (
          <button type="button" onClick={() => viewProofDocument({ name: request.proofFileName, dataUrl: request.proofFileDataUrl })} style={{
            marginTop: 12, display: "flex", alignItems: "center", gap: 8, width: "100%", background: T.tealLight,
            border: "none", borderRadius: 8, padding: "10px 12px", cursor: "pointer", textAlign: "left",
          }}>
            <FileCheck2 size={15} color={T.teal} />
            <span style={{ fontSize: 12.5, fontWeight: 600, color: T.text, flex: 1, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{request.proofFileName}</span>
            <span style={{ fontSize: 11.5, color: T.teal, fontWeight: 600, flexShrink: 0 }}>View</span>
          </button>
        ) : PROOF_REQUIRED_TYPES.includes(request.type) && (
          <div style={{ marginTop: 12, display: "flex", gap: 6, alignItems: "center", color: T.amber, background: T.amberBg, padding: "8px 10px", borderRadius: 6, fontSize: 12 }}>
            <AlertCircle size={13} /> No supporting document was attached for this {request.type.toLowerCase()} request.
          </div>
        )}

        {!approve && (
          <div style={{ marginTop: 16 }}>
            <label style={{ fontSize: 12, fontWeight: 700, color: T.muted }}>Reason for declining</label>
            <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={3}
              style={{ ...inputStyle, marginTop: 5, resize: "vertical" }} placeholder="Explain why this request is being declined…" />
          </div>
        )}

        <div style={{ marginTop: 16 }}>
          <SignaturePad value={signature} onChange={setSignature} height={110} />
          <div style={{ fontSize: 11, color: T.muted, marginTop: 6 }}>
            Signing as <strong>{decider.name}</strong> ({ROLE_LABEL[decider.role]}). This signature and your decision will appear on the letter sent to {empById(request.emp)?.name}.
          </div>
        </div>

        {error && (
          <div style={{ display: "flex", gap: 6, alignItems: "center", color: T.red, background: T.redBg, padding: "8px 10px", borderRadius: 6, fontSize: 12.5, marginTop: 14 }}>
            <AlertCircle size={14} /> {error}
          </div>
        )}

        <div style={{ display: "flex", gap: 8, marginTop: 18 }}>
          <Button variant={approve ? "teal" : "danger"} onClick={confirm}>{approve ? "Confirm Approval" : "Confirm Decline"}</Button>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
        </div>
      </div>
    </div>
  );
}


function ProfileDrawer({ emp, onClose, onUpdateManager }) {
  if (!emp) return null;
  const mgr = emp.manager ? empById(emp.manager) : null;
  const managers = EMPLOYEES.filter((e) => e.role === "manager" && e.id !== emp.id);
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(20,10,9,0.45)", zIndex: 40, display: "flex", justifyContent: "flex-end" }} onClick={onClose}>
      <div style={{ width: 340, background: T.surface, height: "100%", padding: 24, boxSizing: "border-box" }} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
          <div style={{ width: 46, height: 46, borderRadius: "50%", background: T.navy, color: "#fff", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 700, fontSize: 16 }}>{emp.name.split(" ").map((n) => n[0]).join("")}</div>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} /></button>
        </div>
        <div style={{ marginTop: 14, fontSize: 17, fontWeight: 700 }}>{emp.name}</div>
        <div style={{ fontSize: 12.5, color: T.muted, fontFamily: mono }}>{emp.id}</div>
        <div style={{ marginTop: 6, display: "flex", gap: 6 }}>{emp.level && <Pill tone="teal">{emp.level}</Pill>}<RolePill role={emp.role} /></div>
        <div style={{ marginTop: 20, display: "flex", flexDirection: "column", gap: 12, fontSize: 13 }}>
          {[["Position", emp.position], ["Department", emp.dept], ["Email", emp.email], ["Phone", emp.phone || "—"], ["Start Date", emp.start], ["Employment Type", emp.employmentType || "—"], ["Salary", emp.salary > 0 ? money(emp.salary) : "Not set"]].map(([k, v]) => (
            <div key={k}>
              <div style={{ fontSize: 11, color: T.muted, fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.3 }}>{k}</div>
              <div style={{ marginTop: 2 }}>{v}</div>
            </div>
          ))}
          <div>
            <div style={{ fontSize: 11, color: T.muted, fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.3 }}>Reports To</div>
            {onUpdateManager ? (
              <select value={emp.manager || ""} onChange={(e) => onUpdateManager(emp.id, e.target.value)} style={{ ...inputStyle, marginTop: 4, padding: "6px 8px" }}>
                <option value="">— No manager assigned —</option>
                {managers.map((m) => <option key={m.id} value={m.id}>{m.name}</option>)}
              </select>
            ) : (
              <div style={{ marginTop: 2 }}>{mgr ? mgr.name : (emp.managerName || "—")}</div>
            )}
          </div>
        </div>
        <div style={{ marginTop: 20 }}>
          <Button variant="ghost" small icon={FileCheck2} onClick={() => downloadOnboardingDocument(emp)}>Download Onboarding Document</Button>
        </div>
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------------- */
/* AUTH SCREENS — LOGIN & SIGN UP                                         */
/* ---------------------------------------------------------------------- */
function AuthShell({ children }) {
  return (
    <div style={{
      fontFamily: sans, minHeight: 640, maxHeight: 720, borderRadius: 10, overflowY: "auto", border: `1px solid ${T.border}`,
      background: `radial-gradient(circle at 20% 20%, #3A1315, ${T.navyDeep} 62%)`,
      display: "flex", alignItems: "flex-start", justifyContent: "center", padding: "40px 20px",
    }}>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600;650;700&family=IBM+Plex+Mono:wght@400;500;600;700&display=swap');
        * { box-sizing: border-box; }
      `}</style>
      <div style={{ width: 420, maxWidth: "100%" }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", marginBottom: 22 }}>
          <img src={COMPANY.logo} alt={COMPANY.name} style={{ height: 46, objectFit: "contain" }} />
        </div>
        <Card style={{ padding: 28 }}>{children}</Card>
      </div>
    </div>
  );
}

function LoginScreen({ onLogin, goSignup }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setLoading(true);
    const err = await onLogin(email.trim(), password);
    setLoading(false);
    if (err) setError(err); else setError("");
  };

  return (
    <AuthShell>
      <div style={{ fontSize: 19, fontWeight: 700, marginBottom: 4 }}>Log in</div>
      <div style={{ fontSize: 13, color: T.muted, marginBottom: 20 }}>Access your HR, payroll and leave workspace.</div>
      <form onSubmit={submit}>
        <Field label="Email Address">
          <div style={{ position: "relative" }}>
            <Mail size={14} color={T.muted} style={{ position: "absolute", left: 10, top: 11 }} />
            <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" placeholder={`you@${ALLOWED_EMAIL_DOMAIN}`} style={{ ...inputStyle, paddingLeft: 30 }} required />
          </div>
        </Field>
        <Field label="Password">
          <div style={{ position: "relative" }}>
            <Lock size={14} color={T.muted} style={{ position: "absolute", left: 10, top: 11 }} />
            <input value={password} onChange={(e) => setPassword(e.target.value)} type="password" placeholder="••••••••" style={{ ...inputStyle, paddingLeft: 30 }} required />
          </div>
        </Field>
        {error && (
          <div style={{ display: "flex", gap: 6, alignItems: "center", color: T.red, background: T.redBg, padding: "8px 10px", borderRadius: 6, fontSize: 12.5, marginBottom: 14 }}>
            <AlertCircle size={14} /> {error}
          </div>
        )}
        <Button type="submit" variant="teal" full disabled={loading}>{loading ? "Logging in…" : "Log In"}</Button>
      </form>
      <div style={{ marginTop: 16, fontSize: 12.5, color: T.muted, textAlign: "center" }}>
        Don't have an account? <button onClick={goSignup} style={{ background: "none", border: "none", color: T.teal, fontWeight: 700, cursor: "pointer", fontSize: 12.5, padding: 0 }}>Sign up</button>
      </div>
    </AuthShell>
  );
}

function SignupScreen({ onSignup, goLogin }) {
  const allFieldKeys = PERSONAL_INFO_GROUPS.flatMap((g) => g.fields.map(([key]) => key));
  const blankInfo = Object.fromEntries(allFieldKeys.map((k) => [k, ""]));

  const [form, setForm] = useState({
    name: "", email: "", phone: "", password: "", confirm: "",
    role: "employee", dept: DEPARTMENTS[0], position: "", office: OFFICES[0], signature: null,
    ...blankInfo,
  });
  const [sameAsResidential, setSameAsResidential] = useState(true);
  const [agreedToTerms, setAgreedToTerms] = useState(false);
  const [termsOpen, setTermsOpen] = useState(false);
  const [openGroup, setOpenGroup] = useState(PERSONAL_INFO_GROUPS[0].title);
  const [error, setError] = useState("");
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const [submitting, setSubmitting] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (!form.name || !form.email || !form.password) { setError("Please fill in all required fields."); return; }
    if (!isCompanyEmail(form.email)) { setError(`Please use your company email address, ending in @${ALLOWED_EMAIL_DOMAIN}.`); return; }
    if (form.password !== form.confirm) { setError("Passwords do not match."); return; }
    if (EMPLOYEES.some((emp) => emp.email.toLowerCase() === form.email.toLowerCase())) { setError("An account with that email already exists."); return; }

    if (!form.idNumber && !form.passportNumber) {
      setError("Please provide either an Identity Number or a Passport Number.");
      setOpenGroup("Personal Information");
      return;
    }
    for (const group of PERSONAL_INFO_GROUPS) {
      if (group.title === "Postal Address" && sameAsResidential) continue;
      for (const [key, label, , required] of group.fields) {
        if (required && !form[key] && !(key === "idNumber" || key === "passportNumber" || key === "passportCountry")) {
          setError(`Please fill in "${label}" under ${group.title}.`);
          setOpenGroup(group.title);
          return;
        }
      }
    }
    if (!agreedToTerms) { setError("Please agree to the Terms & Conditions to continue."); return; }
    if (!form.signature) { setError("Please sign before creating your account."); return; }

    setError("");
    const finalForm = sameAsResidential
      ? { ...form, ...Object.fromEntries(Object.entries(RESIDENTIAL_TO_POSTAL_MAP).map(([postKey, resKey]) => [postKey, form[resKey]])) }
      : form;
    setSubmitting(true);
    const err = await onSignup(finalForm);
    setSubmitting(false);
    if (err) setError(err);
  };

  return (
    <AuthShell>
      <div style={{ fontSize: 19, fontWeight: 700, marginBottom: 4 }}>Create an account</div>
      <div style={{ fontSize: 13, color: T.muted, marginBottom: 20 }}>Choose the role that matches how you'll use the system.</div>
      <form onSubmit={submit}>
        <Field label="Full Name">
          <input value={form.name} onChange={set("name")} style={inputStyle} placeholder="e.g. Zanele Khumalo" required />
        </Field>
        <div style={{ display: "flex", gap: 10 }}>
          <div style={{ flex: 1 }}>
            <Field label="Email Address">
              <input value={form.email} onChange={set("email")} type="email" style={inputStyle} placeholder={`you@${ALLOWED_EMAIL_DOMAIN}`} required />
              <div style={{ fontSize: 10.5, color: T.muted, marginTop: 4 }}>Must be a @{ALLOWED_EMAIL_DOMAIN} company email.</div>
            </Field>
          </div>
          <div style={{ flex: 1 }}>
            <Field label="Phone">
              <input value={form.phone} onChange={set("phone")} style={inputStyle} placeholder="082 000 0000" />
            </Field>
          </div>
        </div>
        <div style={{ display: "flex", gap: 10 }}>
          <div style={{ flex: 1 }}>
            <Field label="Password">
              <input value={form.password} onChange={set("password")} type="password" style={inputStyle} required />
            </Field>
          </div>
          <div style={{ flex: 1 }}>
            <Field label="Confirm Password">
              <input value={form.confirm} onChange={set("confirm")} type="password" style={inputStyle} required />
            </Field>
          </div>
        </div>

        <div style={{ background: T.bg, borderRadius: 8, padding: "10px 12px", marginBottom: 14, fontSize: 12, color: T.muted, display: "flex", gap: 8, alignItems: "flex-start" }}>
          <ShieldCheck size={15} color={T.teal} style={{ flexShrink: 0, marginTop: 1 }} />
          <span>You're signing up as an <strong style={{ color: T.text }}>Employee</strong>. HR, Admin, IT Support and Manager access are granted afterward by the system owner — they aren't choices you make here.</span>
        </div>

        <div style={{ display: "flex", gap: 10 }}>
          <div style={{ flex: 1 }}>
            <Field label="Department">
              <select value={form.dept} onChange={set("dept")} style={inputStyle}>
                {DEPARTMENTS.map((d) => <option key={d}>{d}</option>)}
              </select>
            </Field>
          </div>
          <div style={{ flex: 1 }}>
            <Field label="Position / Job Title">
              <input value={form.position} onChange={set("position")} style={inputStyle} placeholder="e.g. Junior Developer" />
            </Field>
          </div>
        </div>

        <Field label="Office">
          <select value={form.office} onChange={set("office")} style={inputStyle}>
            {OFFICES.map((o) => <option key={o}>{o}</option>)}
          </select>
          <div style={{ fontSize: 11, color: T.muted, marginTop: 4 }}>Which office you're based at — used to route IT support requests to the right location.</div>
        </Field>

        <div style={{ fontSize: 12.5, fontWeight: 700, color: T.muted, margin: "18px 0 8px", paddingTop: 14, borderTop: `1px solid ${T.border}` }}>
          Onboarding Information
        </div>
        {PERSONAL_INFO_GROUPS.map((group) => (
          <div key={group.title} style={{ marginBottom: 8, border: `1px solid ${T.border}`, borderRadius: 8, overflow: "hidden" }}>
            <button type="button" onClick={() => setOpenGroup(openGroup === group.title ? null : group.title)} style={{
              width: "100%", textAlign: "left", background: T.bg, border: "none", padding: "10px 14px",
              fontSize: 12.5, fontWeight: 700, cursor: "pointer", display: "flex", justifyContent: "space-between", alignItems: "center",
            }}>
              {group.title}
              <ChevronRight size={14} style={{ transform: openGroup === group.title ? "rotate(90deg)" : "none", transition: "transform .15s" }} />
            </button>
            {openGroup === group.title && (
              <div style={{ padding: 14 }}>
                {group.title === "Postal Address" && (
                  <label style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 12.5, marginBottom: 12, cursor: "pointer" }}>
                    <input type="checkbox" checked={sameAsResidential} onChange={(e) => setSameAsResidential(e.target.checked)} />
                    Same as residential address
                  </label>
                )}
                {!(group.title === "Postal Address" && sameAsResidential) && (
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
                    {group.fields.map(([key, label, type, required]) => (
                      <div key={key}>
                        <label style={{ fontSize: 11.5, fontWeight: 700, color: T.muted }}>{label}{required && <span style={{ color: T.red }}> *</span>}</label>
                        <input type={type || "text"} value={form[key]} onChange={set(key)} style={{ ...inputStyle, marginTop: 4 }} />
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
        <div style={{ fontSize: 10.5, color: T.muted, marginBottom: 14 }}>
          Provide either an Identity Number or a Passport Number + Country under Personal Information.
        </div>

        <div style={{ border: `1px solid ${T.border}`, borderRadius: 8, marginBottom: 14, overflow: "hidden" }}>
          <button type="button" onClick={() => setTermsOpen(!termsOpen)} style={{
            width: "100%", textAlign: "left", background: T.bg, border: "none", padding: "10px 14px",
            fontSize: 12.5, fontWeight: 700, cursor: "pointer", display: "flex", justifyContent: "space-between", alignItems: "center",
          }}>
            Terms & Conditions
            <ChevronRight size={14} style={{ transform: termsOpen ? "rotate(90deg)" : "none", transition: "transform .15s" }} />
          </button>
          {termsOpen && (
            <div style={{ padding: 14, fontSize: 11.5, color: T.muted, lineHeight: 1.7, maxHeight: 180, overflowY: "auto" }}>
              <p><strong>Consent to processing of personal information.</strong> By signing up, you consent to {COMPANY.name} collecting, storing and processing the personal information you provide here (including identity/passport details, tax information, banking details, and residential/postal address) for payroll, tax, HR administration, and leave management purposes, in accordance with the Protection of Personal Information Act (POPIA).</p>
              <p><strong>Accuracy declaration.</strong> You declare that the information you have provided is true and correct to the best of your knowledge, and agree to notify HR promptly of any changes.</p>
              <p><strong>Use of banking details.</strong> Your banking details will be used solely for the purpose of paying your salary and will not be shared outside the company except as required by law (e.g. SARS, UIF).</p>
              <p><strong>Signature.</strong> The signature you provide below will appear on the onboarding document HR keeps on file for this account, alongside the information above.</p>
              <p style={{ marginTop: 10, fontStyle: "italic" }}>This is placeholder wording pending review by K and K Media's own legal/HR team — it has not been reviewed by a lawyer and should be replaced with the company's actual approved terms before relying on it.</p>
            </div>
          )}
        </div>

        <label style={{ display: "flex", alignItems: "flex-start", gap: 8, fontSize: 12.5, marginBottom: 14, cursor: "pointer" }}>
          <input type="checkbox" checked={agreedToTerms} onChange={(e) => setAgreedToTerms(e.target.checked)} style={{ marginTop: 2 }} />
          <span>I agree to the Terms &amp; Conditions above and consent to {COMPANY.name} processing this personal information for payroll and HR purposes.</span>
        </label>

        <SignaturePad value={form.signature} onChange={(sig) => setForm({ ...form, signature: sig })} />
        <div style={{ fontSize: 10.5, color: T.muted, margin: "6px 0 14px" }}>
          This signature will appear on the onboarding document HR can download for your account.
        </div>

        {error && (
          <div style={{ display: "flex", gap: 6, alignItems: "center", color: T.red, background: T.redBg, padding: "8px 10px", borderRadius: 6, fontSize: 12.5, marginBottom: 14 }}>
            <AlertCircle size={14} /> {error}
          </div>
        )}
        <Button type="submit" variant="teal" full disabled={submitting}>{submitting ? "Creating account…" : "Create Account"}</Button>
      </form>
      <div style={{ marginTop: 16, fontSize: 12.5, color: T.muted, textAlign: "center" }}>
        Already have an account? <button onClick={goLogin} style={{ background: "none", border: "none", color: T.teal, fontWeight: 700, cursor: "pointer", fontSize: 12.5, padding: 0 }}>Log in</button>
      </div>
    </AuthShell>
  );
}

/* ---------------------------------------------------------------------- */
/* ---------------------------------------------------------------------- */
/* REAL API CLIENT — used when API_BASE_URL is set (i.e. a backend is     */
/* actually deployed). Falls back to local-only behavior everywhere this  */
/* isn't set, so the app still works for local frontend-only development. */
/* ---------------------------------------------------------------------- */
const AUTH_TOKEN_KEY = "kk_auth_token";

function getStoredToken() {
  try { return localStorage.getItem(AUTH_TOKEN_KEY); } catch (e) { return null; }
}
function setStoredToken(token) {
  try { token ? localStorage.setItem(AUTH_TOKEN_KEY, token) : localStorage.removeItem(AUTH_TOKEN_KEY); } catch (e) { /* ignore */ }
}

/** Throws with a human-readable message on any non-2xx response, so
 *  callers can just try/catch and show err.message. */
async function apiFetch(path, options = {}) {
  const token = getStoredToken();
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  if (token) headers.Authorization = `Bearer ${token}`;
  let res;
  try {
    res = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });
  } catch (e) {
    throw new Error("Couldn't reach the server — check your connection and try again.");
  }
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const body = await res.json();
      if (body && body.message) message = body.message;
    } catch (e) { /* body wasn't JSON — keep the generic message */ }
    throw new Error(message);
  }
  if (res.status === 204) return null;
  try { return await res.json(); } catch (e) { return null; }
}

/** Converts the backend's Employee JSON shape (nested department/level
 *  objects, firstName/lastName, numeric id) into the flat shape this
 *  frontend already uses everywhere (dept/level as plain name strings,
 *  a single `name`, `id` set to the human-facing employeeCode). This
 *  lets every existing component keep working unchanged — only the data
 *  source moves from local state to the real API. `_dbId` is kept for
 *  calls that need the real numeric primary key (e.g. role changes). */
function mapBackendEmployee(be) {
  if (!be) return null;
  const onboardingKeys = PERSONAL_INFO_GROUPS.flatMap((g) => g.fields.map(([key]) => key));
  const onboarding = Object.fromEntries(onboardingKeys.map((k) => [k, be[k] ?? ""]));
  return {
    id: be.employeeCode,
    _dbId: be.id,
    name: `${be.firstName || ""} ${be.lastName || ""}`.trim() || be.employeeCode,
    role: (be.role || "employee").toLowerCase(),
    level: be.level ? be.level.name : null,
    position: be.position || "",
    dept: be.department ? be.department.name : "",
    salary: be.salary != null ? Number(be.salary) : 0,
    manager: be.manager ? be.manager.employeeCode : null,
    start: be.startDate || "",
    email: be.email,
    phone: be.phone || "",
    office: be.office || OFFICES[0],
    employmentType: be.employmentType || "",
    agreedToTerms: !!be.agreedToTerms,
    termsAgreedAt: be.termsAgreedAt || null,
    onboardingSignature: be.onboardingSignature || null,
    ...onboarding,
  };
}

// "2026-09" — matches the backend's Payroll.currentPeriod() format, used for
// every payroll API call. CURRENT_MONTH ("September 2026") stays separate,
// purely for display.
const CURRENT_PAY_PERIOD = "2026-09";

/** Converts a backend Payroll row into the same {basic, overtime, bonus,
 *  housing, transport, gross, paye, uif, totalDeductions, net} shape
 *  calcPayroll() already produces locally, so HrPayroll/HrDashboard don't
 *  need separate rendering logic for real vs local figures. */
function mapBackendPayroll(p) {
  const n = (v) => (v != null ? Number(v) : 0);
  return {
    _dbId: p.id,
    empId: p.employee ? p.employee.employeeCode : null,
    status: p.status,
    basic: n(p.basicSalary), overtime: n(p.overtime), bonus: n(p.bonus),
    housing: n(p.housingAllowance), transport: n(p.transportAllowance),
    gross: n(p.grossPay), paye: n(p.paye), uif: n(p.uif),
    totalDeductions: n(p.totalDeductions), net: n(p.netPay),
    payslipId: p.payslipId || null, verificationCode: p.verificationCode || null,
    emailSent: !!p.emailSent, emailSentAt: p.emailSentAt || null, emailFailureReason: p.emailFailureReason || null,
  };
}

/** Backend status is PENDING/APPROVED/REJECTED; the frontend everywhere
 *  expects Pending/Approved/Rejected. Note: the backend has no field for
 *  proof-of-leave file storage yet, so that stays local-display-only even
 *  when connected — applying for leave via the real API never sends it. */
function mapBackendLeaveRequest(lr) {
  const cap = (s) => (s ? s.charAt(0) + s.slice(1).toLowerCase() : "Pending");
  return {
    _dbId: lr.id,
    id: `LR-${lr.id}`,
    emp: lr.employee ? lr.employee.employeeCode : null,
    type: lr.leaveType ? lr.leaveType.name : "",
    start: lr.startDate,
    end: lr.endDate,
    days: lr.daysRequested,
    reason: lr.reason,
    status: cap(lr.status),
    employeeSignature: lr.employeeSignature || null,
    employeeSignedAt: lr.employeeSignedAt || null,
    deciderId: lr.decidedBy ? lr.decidedBy.employeeCode : null,
    deciderName: lr.decidedBy ? lr.decidedBy.getFullName : null,
    deciderSignature: lr.deciderSignature || null,
    deciderSignedAt: lr.deciderSignedAt || null,
    decisionReason: lr.decisionReason || null,
  };
}

/* ---------------------------------------------------------------------- */
/* SETTINGS — edit my profile                                             */
/* ---------------------------------------------------------------------- */
/* ---------------------------------------------------------------------- */
/* SUPPORT CENTER — request help, routed to IT support                    */
/* ---------------------------------------------------------------------- */
/** Calls the real backend directly — no email client, no mailto:. Returns
 *  { ok, reason } rather than throwing, so the UI can show a precise,
 *  honest message for each failure mode instead of a generic error. */
async function sendRequestToServer(path, ticket, emp) {
  if (!API_BASE_URL) {
    return { ok: false, reason: "not-connected" };
  }
  try {
    const res = await fetch(`${API_BASE_URL}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        employeeName: emp.name, employeeCode: emp.id, employeeEmail: emp.email,
        role: ROLE_LABEL[emp.role], department: emp.dept, office: ticket.office || emp.office,
        subject: ticket.subject, category: ticket.category, priority: ticket.priority,
        description: ticket.description,
      }),
    });
    if (!res.ok) return { ok: false, reason: "server-error" };
    return { ok: true };
  } catch (e) {
    return { ok: false, reason: "network-error" };
  }
}

function TicketDetailModal({ ticket, onClose, onSave }) {
  const [status, setStatus] = useState(ticket?.status || "Open");
  const [response, setResponse] = useState(ticket?.response || "");

  if (!ticket) return null;

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(20,10,9,0.5)", zIndex: 55, display: "flex", alignItems: "center", justifyContent: "center", padding: 16 }} onClick={onClose}>
      <div style={{ background: T.surface, width: 480, maxWidth: "100%", borderRadius: 10, padding: 26, boxShadow: "0 20px 60px rgba(0,0,0,.35)" }} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 4 }}>
          <div>
            <div style={{ fontSize: 16, fontWeight: 700 }}>{ticket.subject}</div>
            <div style={{ fontSize: 12.5, color: T.muted, marginTop: 2 }}>{ticket.empName} · {ticket.category} · {ticket.office || "No office set"}</div>
          </div>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} /></button>
        </div>

        <div style={{ marginTop: 14, background: T.bg, borderRadius: 8, padding: 12, fontSize: 13, color: T.text, lineHeight: 1.6, whiteSpace: "pre-wrap" }}>{ticket.description}</div>

        <div style={{ marginTop: 16 }}>
          <label style={{ fontSize: 12, fontWeight: 700, color: T.muted }}>Status</label>
          <select value={status} onChange={(e) => setStatus(e.target.value)} style={{ ...inputStyle, marginTop: 5 }}>
            {TICKET_STATUSES.map((s) => <option key={s}>{s}</option>)}
          </select>
        </div>

        <div style={{ marginTop: 14 }}>
          <label style={{ fontSize: 12, fontWeight: 700, color: T.muted }}>Response to {ticket.empName}</label>
          <textarea value={response} onChange={(e) => setResponse(e.target.value)} rows={4} style={{ ...inputStyle, marginTop: 5, resize: "vertical" }} placeholder="e.g. I'll be at the Sandton office from 2pm today and can look at this then…" />
        </div>

        <div style={{ display: "flex", gap: 8, marginTop: 18 }}>
          <Button variant="teal" onClick={() => onSave(ticket.id, status, response)}>Save</Button>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
        </div>
      </div>
    </div>
  );
}

function SupportCenter({ emp, tickets, onSubmit, onBack, isAdminView, onUpdateTicket }) {
  const [view, setView] = useState(isAdminView ? "all" : "new");
  const [form, setForm] = useState({ subject: "", category: SUPPORT_CATEGORIES[0], priority: "Medium", description: "" });
  const [error, setError] = useState("");
  const [sending, setSending] = useState(false);
  const [justSubmitted, setJustSubmitted] = useState(null);
  const [detailTicket, setDetailTicket] = useState(null);

  const myTickets = tickets.filter((t) => t.empId === emp.id);
  const sortedTickets = [...tickets].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));

  const submit = async () => {
    if (!form.subject.trim() || !form.description.trim()) { setError("Please fill in a subject and description."); return; }
    setError("");
    setSending(true);
    const ticket = {
      id: `TCK-${Math.floor(Math.random() * 9000 + 1000)}`,
      empId: emp.id, empName: emp.name, subject: form.subject.trim(), category: form.category,
      priority: form.priority, description: form.description.trim(), status: "Open",
      createdAt: new Date().toISOString(),
    };
    const result = await sendRequestToServer("/api/public/support", ticket, emp);
    setSending(false);

    if (!result.ok) {
      const messages = {
        "not-connected": "This app isn't connected to the support server yet, so nothing was sent. (The backend needs to be deployed and its URL set in the frontend — see backend/README.md.)",
        "server-error": "The support server received this but rejected it — please try again in a moment.",
        "network-error": "Couldn't reach the support server — check your connection and try again.",
      };
      setError(messages[result.reason] || "Something went wrong sending this request.");
      return;
    }

    onSubmit(ticket);
    setJustSubmitted(ticket);
    setForm({ subject: "", category: SUPPORT_CATEGORIES[0], priority: "Medium", description: "" });
  };

  const tabs = isAdminView
    ? [{ id: "all", label: "All Support Tickets" }]
    : [{ id: "new", label: "New Request" }, { id: "mine", label: "My Requests" }];

  const statusTone = (s) => s === "Resolved" ? "green" : s === "In Progress" ? "amber" : "teal";
  const priorityTone = (p) => p === "Urgent" ? "red" : p === "High" ? "amber" : "muted";

  return (
    <div>
      {onBack && (
        <button onClick={onBack} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "none", color: T.muted, fontSize: 13, fontWeight: 600, cursor: "pointer", padding: 0, marginBottom: 14 }}>
          <ArrowLeft size={15} /> Back
        </button>
      )}
      <SectionTitle sub={`For issues within the system itself — payslips, leave, bugs. Sent directly to ${SUPPORT_EMAIL}.`}>Support</SectionTitle>

      <div style={{ display: "flex", gap: 6, marginBottom: 20, borderBottom: `1px solid ${T.border}` }}>
        {tabs.map((t) => (
          <button key={t.id} onClick={() => setView(t.id)} style={{ background: "none", border: "none", cursor: "pointer", padding: "8px 4px", fontSize: 13, fontWeight: 600, color: view === t.id ? T.navy : T.muted, borderBottom: view === t.id ? `2px solid ${T.teal}` : "2px solid transparent" }}>{t.label}</button>
        ))}
      </div>

      {view === "new" && (
        <Card style={{ padding: 22, maxWidth: 480 }}>
          {justSubmitted ? (
            <div>
              <div style={{ display: "flex", alignItems: "center", gap: 8, color: T.green, marginBottom: 10 }}>
                <CheckCircle2 size={18} /> <span style={{ fontWeight: 700, fontSize: 14 }}>Sent</span>
              </div>
              <div style={{ fontSize: 13, color: T.muted, lineHeight: 1.7, marginBottom: 16 }}>
                Your request was sent directly to <strong>{SUPPORT_EMAIL}</strong> — no email app needed. You'll be contacted there if more details are needed.
              </div>
              <Button variant="ghost" small onClick={() => setJustSubmitted(null)}>Submit Another</Button>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              {!API_BASE_URL && (
                <div style={{ display: "flex", gap: 6, alignItems: "center", color: T.amber, background: T.amberBg, padding: "8px 10px", borderRadius: 6, fontSize: 12 }}>
                  <AlertCircle size={13} /> Not connected to the support server yet — sending will fail until the backend is deployed.
                </div>
              )}
              <div style={{ fontSize: 11.5, color: T.muted, background: T.bg, padding: "8px 10px", borderRadius: 6 }}>
                For things you're running into <strong>within this system</strong> — a payslip that looks wrong, trouble with a leave application, a bug, or an account/access problem. Have a physical issue at your office instead (hardware, network, printer)? Use <strong>Office Issues</strong> in the sidebar instead.
              </div>
              <div>
                <label style={{ fontSize: 12, fontWeight: 700, color: T.muted }}>Subject</label>
                <input value={form.subject} onChange={(e) => setForm({ ...form, subject: e.target.value })} style={{ ...inputStyle, marginTop: 5 }} placeholder="Brief summary of the issue" />
              </div>
              <div style={{ display: "flex", gap: 10 }}>
                <div style={{ flex: 1 }}>
                  <label style={{ fontSize: 12, fontWeight: 700, color: T.muted }}>Category</label>
                  <select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} style={{ ...inputStyle, marginTop: 5 }}>
                    {SUPPORT_CATEGORIES.map((c) => <option key={c}>{c}</option>)}
                  </select>
                </div>
                <div style={{ flex: 1 }}>
                  <label style={{ fontSize: 12, fontWeight: 700, color: T.muted }}>Priority</label>
                  <select value={form.priority} onChange={(e) => setForm({ ...form, priority: e.target.value })} style={{ ...inputStyle, marginTop: 5 }}>
                    {SUPPORT_PRIORITIES.map((p) => <option key={p}>{p}</option>)}
                  </select>
                </div>
              </div>
              <div>
                <label style={{ fontSize: 12, fontWeight: 700, color: T.muted }}>Describe the problem</label>
                <textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} rows={5} style={{ ...inputStyle, marginTop: 5, resize: "vertical" }} placeholder="What happened, what you expected, and when it started…" />
              </div>
              {error && (
                <div style={{ display: "flex", gap: 6, alignItems: "center", color: T.red, background: T.redBg, padding: "8px 10px", borderRadius: 6, fontSize: 12.5 }}>
                  <AlertCircle size={14} /> {error}
                </div>
              )}
              <Button variant="teal" icon={Mail} disabled={sending} onClick={submit}>{sending ? "Sending…" : "Send to Support"}</Button>
            </div>
          )}
        </Card>
      )}

      {view === "mine" && (
        <Card style={{ overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead><tr style={{ background: T.bg, textAlign: "left" }}>{["Subject", "Category", "Priority", "Status", "Response", "Submitted"].map((h) => <th key={h} style={{ padding: "10px 14px", fontSize: 11.5, color: T.muted, fontWeight: 700 }}>{h}</th>)}</tr></thead>
            <tbody>
              {myTickets.length === 0 && <tr><td colSpan={6} style={{ padding: 18, textAlign: "center", color: T.muted }}>No support requests yet.</td></tr>}
              {myTickets.map((t) => (
                <tr key={t.id} style={{ borderTop: `1px solid ${T.border}` }}>
                  <td style={{ padding: "10px 14px", fontWeight: 600 }}>{t.subject}</td>
                  <td style={{ padding: "10px 14px", color: T.muted }}>{t.category}</td>
                  <td style={{ padding: "10px 14px" }}><Pill tone={priorityTone(t.priority)}>{t.priority}</Pill></td>
                  <td style={{ padding: "10px 14px" }}><Pill tone={statusTone(t.status)}>{t.status}</Pill></td>
                  <td style={{ padding: "10px 14px", color: T.muted, maxWidth: 220, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{t.response || "—"}</td>
                  <td style={{ padding: "10px 14px", fontFamily: mono, fontSize: 12 }}>{new Date(t.createdAt).toLocaleDateString("en-ZA")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {view === "all" && (
        <Card style={{ overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead><tr style={{ background: T.bg, textAlign: "left" }}>{["Employee", "Subject", "Category", "Priority", "Status", ""].map((h) => <th key={h} style={{ padding: "10px 14px", fontSize: 11.5, color: T.muted, fontWeight: 700 }}>{h}</th>)}</tr></thead>
            <tbody>
              {sortedTickets.length === 0 && <tr><td colSpan={6} style={{ padding: 18, textAlign: "center", color: T.muted }}>No support requests yet.</td></tr>}
              {sortedTickets.map((t) => (
                <tr key={t.id} style={{ borderTop: `1px solid ${T.border}` }}>
                  <td style={{ padding: "10px 14px" }}>{t.empName}</td>
                  <td style={{ padding: "10px 14px" }}>{t.subject}</td>
                  <td style={{ padding: "10px 14px", color: T.muted }}>{t.category}</td>
                  <td style={{ padding: "10px 14px" }}><Pill tone={priorityTone(t.priority)}>{t.priority}</Pill></td>
                  <td style={{ padding: "10px 14px" }}><Pill tone={statusTone(t.status)}>{t.status}</Pill></td>
                  <td style={{ padding: "10px 14px" }}>
                    <button onClick={() => setDetailTicket(t)} style={{ background: "none", border: "none", cursor: "pointer", color: T.teal, fontWeight: 600, fontSize: 12.5 }}>Manage</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      <TicketDetailModal ticket={detailTicket} onClose={() => setDetailTicket(null)}
        onSave={(id, status, response) => { onUpdateTicket(id, status, response); setDetailTicket(null); }} />
    </div>
  );
}

/* ---------------------------------------------------------------------- */
/* OFFICE ISSUES — separate feature: on-site hardware/network/equipment   */
/* issues at a specific office (Midrand/Sandton), managed by IT Support   */
/* ---------------------------------------------------------------------- */
function OfficeIssueCenter({ emp, issues, onSubmit, onBack, isAdminView, availability, onSetAvailability, onUpdateIssue }) {
  const [view, setView] = useState(isAdminView ? "all" : "new");
  const [form, setForm] = useState({ subject: "", type: OFFICE_ISSUE_TYPES[0], priority: "Medium", description: "", office: emp.office || OFFICES[0] });
  const [error, setError] = useState("");
  const [sending, setSending] = useState(false);
  const [justSubmitted, setJustSubmitted] = useState(null);
  const [detailIssue, setDetailIssue] = useState(null);
  const [availabilityDraft, setAvailabilityDraft] = useState(availability || "");

  const myIssues = issues.filter((i) => i.empId === emp.id);
  const sortedIssues = [...issues].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));

  const submit = async () => {
    if (!form.subject.trim() || !form.description.trim()) { setError("Please fill in a subject and description."); return; }
    setError("");
    setSending(true);
    const issue = {
      id: `OFF-${Math.floor(Math.random() * 9000 + 1000)}`,
      empId: emp.id, empName: emp.name, subject: form.subject.trim(), category: form.type,
      priority: form.priority, description: form.description.trim(), status: "Open", office: form.office,
      createdAt: new Date().toISOString(),
    };
    const result = await sendRequestToServer("/api/public/office-issues", issue, emp);
    setSending(false);

    if (!result.ok) {
      const messages = {
        "not-connected": "This app isn't connected to the support server yet, so nothing was sent. (The backend needs to be deployed and its URL set in the frontend — see backend/README.md.)",
        "server-error": "The server received this but rejected it — please try again in a moment.",
        "network-error": "Couldn't reach the server — check your connection and try again.",
      };
      setError(messages[result.reason] || "Something went wrong sending this request.");
      return;
    }

    onSubmit(issue);
    setJustSubmitted(issue);
    setForm({ subject: "", type: OFFICE_ISSUE_TYPES[0], priority: "Medium", description: "", office: emp.office || OFFICES[0] });
  };

  const tabs = isAdminView
    ? [{ id: "all", label: "All Office Issues" }]
    : [{ id: "new", label: "Report an Issue" }, { id: "mine", label: "My Reports" }];

  const statusTone = (s) => s === "Resolved" ? "green" : s === "In Progress" ? "amber" : "teal";
  const priorityTone = (p) => p === "Urgent" ? "red" : p === "High" ? "amber" : "muted";

  return (
    <div>
      {onBack && (
        <button onClick={onBack} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "none", color: T.muted, fontSize: 13, fontWeight: 600, cursor: "pointer", padding: 0, marginBottom: 14 }}>
          <ArrowLeft size={15} /> Back
        </button>
      )}
      <SectionTitle sub="For physical/on-site issues at your office — hardware, network, printers, equipment">Office Issues</SectionTitle>

      {isAdminView && (
        <Card style={{ padding: 16, marginBottom: 20 }}>
          <div style={{ fontSize: 12.5, fontWeight: 700, color: T.muted, marginBottom: 8 }}>Your Availability (shown to employees before they report an issue)</div>
          <div style={{ display: "flex", gap: 8 }}>
            <input value={availabilityDraft} onChange={(e) => setAvailabilityDraft(e.target.value)} style={{ ...inputStyle, flex: 1 }} placeholder="e.g. At the Midrand office until 1pm, then Sandton" />
            <Button variant="teal" small onClick={() => onSetAvailability(availabilityDraft)}>Save</Button>
          </div>
        </Card>
      )}

      {!isAdminView && availability && (
        <Card style={{ padding: "10px 14px", marginBottom: 16, display: "flex", alignItems: "center", gap: 8, background: T.tealLight }}>
          <MapPin size={14} color={T.teal} />
          <span style={{ fontSize: 12.5, color: T.text }}><strong>IT Support availability:</strong> {availability}</span>
        </Card>
      )}

      <div style={{ display: "flex", gap: 6, marginBottom: 20, borderBottom: `1px solid ${T.border}` }}>
        {tabs.map((t) => (
          <button key={t.id} onClick={() => setView(t.id)} style={{ background: "none", border: "none", cursor: "pointer", padding: "8px 4px", fontSize: 13, fontWeight: 600, color: view === t.id ? T.navy : T.muted, borderBottom: view === t.id ? `2px solid ${T.teal}` : "2px solid transparent" }}>{t.label}</button>
        ))}
      </div>

      {view === "new" && (
        <Card style={{ padding: 22, maxWidth: 480 }}>
          {justSubmitted ? (
            <div>
              <div style={{ display: "flex", alignItems: "center", gap: 8, color: T.green, marginBottom: 10 }}>
                <CheckCircle2 size={18} /> <span style={{ fontWeight: 700, fontSize: 14 }}>Sent</span>
              </div>
              <div style={{ fontSize: 13, color: T.muted, lineHeight: 1.7, marginBottom: 16 }}>
                Your report was sent directly to IT support. Check "My Reports" for updates and to see when they'll be at your office.
              </div>
              <Button variant="ghost" small onClick={() => setJustSubmitted(null)}>Report Another</Button>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              {!API_BASE_URL && (
                <div style={{ display: "flex", gap: 6, alignItems: "center", color: T.amber, background: T.amberBg, padding: "8px 10px", borderRadius: 6, fontSize: 12 }}>
                  <AlertCircle size={13} /> Not connected to the support server yet — sending will fail until the backend is deployed.
                </div>
              )}
              <div>
                <label style={{ fontSize: 12, fontWeight: 700, color: T.muted }}>Subject</label>
                <input value={form.subject} onChange={(e) => setForm({ ...form, subject: e.target.value })} style={{ ...inputStyle, marginTop: 5 }} placeholder="Brief summary of the issue" />
              </div>
              <div style={{ display: "flex", gap: 10 }}>
                <div style={{ flex: 1 }}>
                  <label style={{ fontSize: 12, fontWeight: 700, color: T.muted }}>Issue Type</label>
                  <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })} style={{ ...inputStyle, marginTop: 5 }}>
                    {OFFICE_ISSUE_TYPES.map((t) => <option key={t}>{t}</option>)}
                  </select>
                </div>
                <div style={{ flex: 1 }}>
                  <label style={{ fontSize: 12, fontWeight: 700, color: T.muted }}>Priority</label>
                  <select value={form.priority} onChange={(e) => setForm({ ...form, priority: e.target.value })} style={{ ...inputStyle, marginTop: 5 }}>
                    {SUPPORT_PRIORITIES.map((p) => <option key={p}>{p}</option>)}
                  </select>
                </div>
              </div>
              <div>
                <label style={{ fontSize: 12, fontWeight: 700, color: T.muted }}>Office</label>
                <select value={form.office} onChange={(e) => setForm({ ...form, office: e.target.value })} style={{ ...inputStyle, marginTop: 5 }}>
                  {OFFICES.map((o) => <option key={o}>{o}</option>)}
                </select>
                <div style={{ fontSize: 11, color: T.muted, marginTop: 4 }}>So IT support knows which office to go to.</div>
              </div>
              <div>
                <label style={{ fontSize: 12, fontWeight: 700, color: T.muted }}>Describe the problem</label>
                <textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} rows={5} style={{ ...inputStyle, marginTop: 5, resize: "vertical" }} placeholder="What's the equipment/location, and what's happening?" />
              </div>
              {error && (
                <div style={{ display: "flex", gap: 6, alignItems: "center", color: T.red, background: T.redBg, padding: "8px 10px", borderRadius: 6, fontSize: 12.5 }}>
                  <AlertCircle size={14} /> {error}
                </div>
              )}
              <Button variant="teal" icon={MapPin} disabled={sending} onClick={submit}>{sending ? "Sending…" : "Report to IT Support"}</Button>
            </div>
          )}
        </Card>
      )}

      {view === "mine" && (
        <Card style={{ overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead><tr style={{ background: T.bg, textAlign: "left" }}>{["Subject", "Type", "Office", "Priority", "Status", "Response", "Reported"].map((h) => <th key={h} style={{ padding: "10px 14px", fontSize: 11.5, color: T.muted, fontWeight: 700 }}>{h}</th>)}</tr></thead>
            <tbody>
              {myIssues.length === 0 && <tr><td colSpan={7} style={{ padding: 18, textAlign: "center", color: T.muted }}>No office issues reported yet.</td></tr>}
              {myIssues.map((i) => (
                <tr key={i.id} style={{ borderTop: `1px solid ${T.border}` }}>
                  <td style={{ padding: "10px 14px", fontWeight: 600 }}>{i.subject}</td>
                  <td style={{ padding: "10px 14px", color: T.muted }}>{i.category}</td>
                  <td style={{ padding: "10px 14px" }}>{i.office || "—"}</td>
                  <td style={{ padding: "10px 14px" }}><Pill tone={priorityTone(i.priority)}>{i.priority}</Pill></td>
                  <td style={{ padding: "10px 14px" }}><Pill tone={statusTone(i.status)}>{i.status}</Pill></td>
                  <td style={{ padding: "10px 14px", color: T.muted, maxWidth: 200, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{i.response || "—"}</td>
                  <td style={{ padding: "10px 14px", fontFamily: mono, fontSize: 12 }}>{new Date(i.createdAt).toLocaleDateString("en-ZA")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {view === "all" && (
        <Card style={{ overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead><tr style={{ background: T.bg, textAlign: "left" }}>{["Employee", "Office", "Subject", "Type", "Priority", "Status", ""].map((h) => <th key={h} style={{ padding: "10px 14px", fontSize: 11.5, color: T.muted, fontWeight: 700 }}>{h}</th>)}</tr></thead>
            <tbody>
              {sortedIssues.length === 0 && <tr><td colSpan={7} style={{ padding: 18, textAlign: "center", color: T.muted }}>No office issues reported yet.</td></tr>}
              {sortedIssues.map((i) => (
                <tr key={i.id} style={{ borderTop: `1px solid ${T.border}` }}>
                  <td style={{ padding: "10px 14px" }}>{i.empName}</td>
                  <td style={{ padding: "10px 14px", fontWeight: 600 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 5 }}><MapPin size={13} color={T.teal} />{i.office || "—"}</div>
                  </td>
                  <td style={{ padding: "10px 14px" }}>{i.subject}</td>
                  <td style={{ padding: "10px 14px", color: T.muted }}>{i.category}</td>
                  <td style={{ padding: "10px 14px" }}><Pill tone={priorityTone(i.priority)}>{i.priority}</Pill></td>
                  <td style={{ padding: "10px 14px" }}><Pill tone={statusTone(i.status)}>{i.status}</Pill></td>
                  <td style={{ padding: "10px 14px" }}>
                    <button onClick={() => setDetailIssue(i)} style={{ background: "none", border: "none", cursor: "pointer", color: T.teal, fontWeight: 600, fontSize: 12.5 }}>Manage</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      <TicketDetailModal ticket={detailIssue} onClose={() => setDetailIssue(null)}
        onSave={(id, status, response) => { onUpdateIssue(id, status, response); setDetailIssue(null); }} />
    </div>
  );
}

const PERSONAL_INFO_GROUPS = [
  {
    title: "Personal Information",
    fields: [
      ["title", "Title", "text", true], ["initials", "Initials", "text", true], ["secondName", "Second Name", "text", false],
      ["dateOfBirth", "Date of Birth", "date", true], ["idNumber", "Identity Number", "text", false],
      ["passportNumber", "Passport Number", "text", false], ["passportCountry", "Passport Country", "text", false],
      ["race", "Race", "text", true], ["relationshipStatus", "Relationship Status", "text", true],
      ["contactTelephone", "Contact Telephone", "text", true], ["contactCellphone", "Contact Cellphone", "text", true],
      ["emergencyContactName", "Emergency Contact Name", "text", true],
      ["emergencyContactTelephone", "Emergency Contact Telephone", "text", true],
      ["emergencyContactCellphone", "Emergency Contact Cellphone", "text", false],
    ],
  },
  {
    title: "Tax",
    fields: [["taxOffice", "Tax Office", "text", true], ["incomeTaxNumber", "Income Tax Number", "text", true]],
  },
  {
    title: "Banking Details",
    fields: [
      ["bankAccountType", "Type of Account", "text", true], ["bankBranchCode", "Branch Code", "text", true],
      ["bankName", "Bank Name", "text", true], ["bankBranchName", "Branch Name", "text", false],
      ["bankAccountNumber", "Bank Account Number", "text", true], ["bankAccountHolder", "Account Holder", "text", true],
      ["bankAccountRelationship", "Account Relationship", "text", false],
    ],
  },
  {
    title: "Residential Address",
    fields: [
      ["resUnitNumber", "Unit Number", "text", false], ["resComplexName", "Complex Name", "text", false],
      ["resStreetNumber", "Street Number", "text", true], ["resStreetName", "Street Name", "text", true],
      ["resSuburb", "Suburb", "text", true], ["resCity", "City", "text", true], ["resPostalCode", "Postal Code", "text", true],
    ],
  },
  {
    title: "Postal Address",
    fields: [
      ["postalService", "Postal Service", "text", false], ["postalNumber", "Postal Number", "text", false],
      ["postStreetNumber", "Street Number", "text", false], ["postStreetName", "Street Name", "text", false],
      ["postSuburb", "Suburb", "text", false], ["postCity", "City", "text", false], ["postPostalCode", "Postal Code", "text", false],
    ],
  },
];

const RESIDENTIAL_TO_POSTAL_MAP = {
  postStreetNumber: "resStreetNumber", postStreetName: "resStreetName",
  postSuburb: "resSuburb", postCity: "resCity", postPostalCode: "resPostalCode",
};


function PersonalInfoSection({ emp, onSave }) {
  const initial = {};
  PERSONAL_INFO_GROUPS.forEach((g) => g.fields.forEach(([key]) => { initial[key] = emp[key] || ""; }));
  const [form, setForm] = useState(initial);
  const [saved, setSaved] = useState(false);
  const [openGroup, setOpenGroup] = useState(PERSONAL_INFO_GROUPS[0].title);

  const submit = (e) => {
    e.preventDefault();
    onSave(form);
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  };

  return (
    <Card style={{ padding: 20 }}>
      <div style={{ fontSize: 13.5, fontWeight: 650, marginBottom: 4 }}>Personal & Payroll Information</div>
      <div style={{ fontSize: 12, color: T.muted, marginBottom: 16 }}>
        Used for tax, banking, and emergency contact purposes. Fill in as much as you have on hand — you can always come back and finish the rest later.
      </div>
      <form onSubmit={submit}>
        {PERSONAL_INFO_GROUPS.map((group) => (
          <div key={group.title} style={{ marginBottom: 8, border: `1px solid ${T.border}`, borderRadius: 8, overflow: "hidden" }}>
            <button type="button" onClick={() => setOpenGroup(openGroup === group.title ? null : group.title)} style={{
              width: "100%", textAlign: "left", background: T.bg, border: "none", padding: "10px 14px",
              fontSize: 12.5, fontWeight: 700, cursor: "pointer", display: "flex", justifyContent: "space-between", alignItems: "center",
            }}>
              {group.title}
              <ChevronRight size={14} style={{ transform: openGroup === group.title ? "rotate(90deg)" : "none", transition: "transform .15s" }} />
            </button>
            {openGroup === group.title && (
              <div style={{ padding: 14, display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
                {group.fields.map(([key, label, type, required]) => (
                  <div key={key}>
                    <label style={{ fontSize: 11.5, fontWeight: 700, color: T.muted }}>{label}{required && <span style={{ color: T.red }}> *</span>}</label>
                    <input type={type || "text"} value={form[key]} onChange={(e) => setForm({ ...form, [key]: e.target.value })} style={{ ...inputStyle, marginTop: 4 }} />
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}
        <div style={{ display: "flex", gap: 10, alignItems: "center", marginTop: 12 }}>
          <Button type="submit" variant="teal" small>Save Information</Button>
          {saved && <Pill tone="green">Saved</Pill>}
        </div>
      </form>
    </Card>
  );
}

function Settings({ emp, onSaveProfile, onChangePassword, onBack }) {
  const [form, setForm] = useState({ name: emp.name, email: emp.email, phone: emp.phone || "", position: emp.position, dept: emp.dept, office: emp.office || OFFICES[0] });
  const [saved, setSaved] = useState(false);
  const [pw, setPw] = useState({ current: "", next: "", confirm: "" });
  const [pwMsg, setPwMsg] = useState(null);
  const [notify, setNotify] = useState({ leave: true, payslip: true });

  const saveProfile = (e) => {
    e.preventDefault();
    onSaveProfile(form);
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  };

  const savePassword = (e) => {
    e.preventDefault();
    const expected = emp.password || DEFAULT_PASSWORD;
    if (pw.current !== expected) { setPwMsg({ tone: "red", text: "Current password is incorrect." }); return; }
    if (pw.next.length < 6) { setPwMsg({ tone: "red", text: "New password must be at least 6 characters." }); return; }
    if (pw.next !== pw.confirm) { setPwMsg({ tone: "red", text: "New passwords do not match." }); return; }
    onChangePassword(pw.next);
    setPw({ current: "", next: "", confirm: "" });
    setPwMsg({ tone: "green", text: "Password updated." });
  };

  return (
    <div>
      <button onClick={onBack} style={{
        display: "flex", alignItems: "center", gap: 6, background: "none", border: "none",
        color: T.muted, fontSize: 13, fontWeight: 600, cursor: "pointer", padding: 0, marginBottom: 14,
      }}>
        <ArrowLeft size={15} /> Back
      </button>
      <SectionTitle sub="Update your personal information, password and notification preferences">Settings</SectionTitle>
      <div style={{ display: "flex", gap: 20, flexWrap: "wrap" }}>
        <Card style={{ padding: 20, flex: "1 1 320px" }}>
          <div style={{ fontSize: 13.5, fontWeight: 650, marginBottom: 14 }}>My Profile</div>
          <form onSubmit={saveProfile}>
            <Field label="Full Name"><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} style={inputStyle} /></Field>
            <Field label="Email"><input value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} type="email" style={inputStyle} /></Field>
            <Field label="Phone"><input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} style={inputStyle} /></Field>
            <div style={{ display: "flex", gap: 10 }}>
              <div style={{ flex: 1 }}>
                <Field label="Position"><input value={form.position} onChange={(e) => setForm({ ...form, position: e.target.value })} style={inputStyle} /></Field>
              </div>
              <div style={{ flex: 1 }}>
                <Field label="Department">
                  <select value={form.dept} onChange={(e) => setForm({ ...form, dept: e.target.value })} style={inputStyle}>
                    {DEPARTMENTS.map((d) => <option key={d}>{d}</option>)}
                  </select>
                </Field>
              </div>
            </div>
            <Field label="Office">
              <select value={form.office} onChange={(e) => setForm({ ...form, office: e.target.value })} style={inputStyle}>
                {OFFICES.map((o) => <option key={o}>{o}</option>)}
              </select>
            </Field>
            <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
              <Button type="submit" variant="teal" small>Save Changes</Button>
              {saved && <Pill tone="green">Saved</Pill>}
            </div>
          </form>
        </Card>

        <div style={{ display: "flex", flexDirection: "column", gap: 20, flex: "1 1 320px" }}>
          <Card style={{ padding: 20 }}>
            <div style={{ fontSize: 13.5, fontWeight: 650, marginBottom: 14 }}>Change Password</div>
            <form onSubmit={savePassword}>
              <Field label="Current Password"><input value={pw.current} onChange={(e) => setPw({ ...pw, current: e.target.value })} type="password" style={inputStyle} /></Field>
              <Field label="New Password"><input value={pw.next} onChange={(e) => setPw({ ...pw, next: e.target.value })} type="password" style={inputStyle} /></Field>
              <Field label="Confirm New Password"><input value={pw.confirm} onChange={(e) => setPw({ ...pw, confirm: e.target.value })} type="password" style={inputStyle} /></Field>
              {pwMsg && (
                <div style={{ marginBottom: 12 }}><Pill tone={pwMsg.tone}>{pwMsg.text}</Pill></div>
              )}
              <Button type="submit" variant="ghost" small>Update Password</Button>
            </form>
          </Card>

          <Card style={{ padding: 20 }}>
            <div style={{ fontSize: 13.5, fontWeight: 650, marginBottom: 4 }}>Notification Preferences</div>
            <div style={{ fontSize: 12, color: T.muted, marginBottom: 14 }}>Choose what gets emailed to you.</div>
            {[["leave", "Email me when my leave is approved or rejected"], ["payslip", "Email me when a new payslip is available"]].map(([k, label]) => (
              <label key={k} style={{ display: "flex", alignItems: "center", gap: 9, fontSize: 13, marginBottom: 10, cursor: "pointer" }}>
                <input type="checkbox" checked={notify[k]} onChange={() => setNotify({ ...notify, [k]: !notify[k] })} />
                {label}
              </label>
            ))}
          </Card>

          <Card style={{ padding: 20 }}>
            <div style={{ fontSize: 13.5, fontWeight: 650, marginBottom: 10 }}>Account</div>
            {[["Employee ID", emp.id], ["Role", ROLE_LABEL[emp.role]], ["Start Date", emp.start]].map(([k, v]) => (
              <div key={k} style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", fontSize: 13 }}>
                <span style={{ color: T.muted }}>{k}</span><span style={{ fontFamily: k === "Employee ID" ? mono : sans }}>{v}</span>
              </div>
            ))}
          </Card>
        </div>
      </div>

      <div style={{ marginTop: 20 }}>
        <PersonalInfoSection emp={emp} onSave={onSaveProfile} />
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------------- */
/* HR AREA                                                                */
/* ---------------------------------------------------------------------- */
function HrDashboard({ leaveRequests, payrollStage, advanceStage }) {
  const counts = LEVELS.reduce((acc, l) => { acc[l.name] = EMPLOYEES.filter((e) => e.level === l.name).length; return acc; }, {});
  const pending = leaveRequests.filter((r) => r.status === "Pending").length;
  const stageIdx = STAGES.indexOf(payrollStage);
  return (
    <div>
      <SectionTitle sub="People and payroll operations for K and K Media">HR Dashboard</SectionTitle>
      <div style={{ display: "flex", gap: 14, flexWrap: "wrap", marginBottom: 22 }}>
        <StatCard icon={Users} label="Total Employees" value={EMPLOYEES.length} />
        <StatCard icon={ClipboardList} label="Pending Leave" value={pending} tone={T.amber} />
        <StatCard icon={Banknote} label="Payroll Status" value={payrollStage} tone={T.teal} />
        <StatCard icon={FileText} label="Payslips Sent (Aug)" value={`${EMPLOYEES.length}/${EMPLOYEES.length}`} tone={T.green} />
      </div>
      <div style={{ display: "flex", gap: 20, alignItems: "flex-start", flexWrap: "wrap" }}>
        <Card style={{ padding: 18, flex: "1 1 420px" }}>
          <div style={{ fontSize: 13.5, fontWeight: 650, marginBottom: 14 }}>Employees by Level</div>
          {LEVELS.map((l) => (
            <div key={l.name} style={{ marginBottom: 10 }}>
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12.5, marginBottom: 4 }}>
                <span style={{ color: T.muted }}>{l.name}</span><span style={{ fontFamily: mono, fontWeight: 600 }}>{counts[l.name]}</span>
              </div>
              <div style={{ height: 6, background: "#EEF0F3", borderRadius: 3 }}><div style={{ height: 6, borderRadius: 3, background: T.teal, width: `${(counts[l.name] / EMPLOYEES.length) * 100}%` }} /></div>
            </div>
          ))}
        </Card>
        <Card style={{ padding: 18, flex: "1 1 420px" }}>
          <div style={{ fontSize: 13.5, fontWeight: 650, marginBottom: 4 }}>Payroll Pipeline — {CURRENT_MONTH}</div>
          <div style={{ fontSize: 12, color: T.muted, marginBottom: 16 }}>Advance the batch through review and approval before payslips are sent.</div>
          <div style={{ display: "flex", alignItems: "center", flexWrap: "wrap", gap: 4, marginBottom: 16 }}>
            {STAGES.map((s, i) => (
              <React.Fragment key={s}>
                <div style={{ fontSize: 11, fontWeight: 700, padding: "5px 9px", borderRadius: 5, background: i <= stageIdx ? T.navy : "#EEF0F3", color: i <= stageIdx ? "#fff" : T.muted }}>{s}</div>
                {i < STAGES.length - 1 && <ArrowRight size={12} color={T.muted} />}
              </React.Fragment>
            ))}
          </div>
          {stageIdx < STAGES.length - 1 ? <Button variant="teal" icon={ArrowRight} small onClick={advanceStage}>Advance to {STAGES[stageIdx + 1]}</Button> : <Pill tone="green">All payslips sent for {CURRENT_MONTH}</Pill>}
        </Card>
      </div>
      <div style={{ marginTop: 22 }}>
        <SectionTitle>Recent Leave Requests</SectionTitle>
        <Card style={{ overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead><tr style={{ background: T.bg, textAlign: "left" }}>{["Employee", "Type", "Dates", "Status"].map((h) => <th key={h} style={{ padding: "10px 14px", fontSize: 11.5, color: T.muted, fontWeight: 700 }}>{h}</th>)}</tr></thead>
            <tbody>
              {leaveRequests.slice(0, 5).map((r) => {
                const e = empById(r.emp);
                return (
                  <tr key={r.id} style={{ borderTop: `1px solid ${T.border}` }}>
                    <td style={{ padding: "10px 14px" }}>{e.name}</td>
                    <td style={{ padding: "10px 14px", color: T.muted }}>{r.type}</td>
                    <td style={{ padding: "10px 14px", fontFamily: mono, fontSize: 12 }}>{r.start} → {r.end}</td>
                    <td style={{ padding: "10px 14px" }}><StatusPill status={r.status} /></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </Card>
      </div>
    </div>
  );
}

function EditSalaryModal({ employee, onClose, onSave }) {
  const [value, setValue] = useState(employee ? String(employee.salary) : "");
  if (!employee) return null;
  const submit = () => {
    const num = parseFloat(value);
    if (!isNaN(num) && num >= 0) onSave(employee.id, num);
  };
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(20,10,9,0.5)", zIndex: 55, display: "flex", alignItems: "center", justifyContent: "center", padding: 16 }} onClick={onClose}>
      <div style={{ background: T.surface, width: 380, maxWidth: "100%", borderRadius: 10, padding: 24, boxShadow: "0 20px 60px rgba(0,0,0,.35)" }} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 14 }}>
          <div>
            <div style={{ fontSize: 15, fontWeight: 700 }}>Edit Salary</div>
            <div style={{ fontSize: 12.5, color: T.muted, marginTop: 2 }}>{employee.name}{employee.position && ` · ${employee.position}`}</div>
          </div>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} /></button>
        </div>
        <label style={{ fontSize: 12, fontWeight: 700, color: T.muted }}>Monthly Salary (R)</label>
        <input type="number" value={value} onChange={(e) => setValue(e.target.value)} style={{ ...inputStyle, marginTop: 5 }} />
        <div style={{ fontSize: 11, color: T.muted, marginTop: 6 }}>This is used the next time {employee.name}'s payroll is generated.</div>
        <div style={{ display: "flex", gap: 8, marginTop: 18 }}>
          <Button variant="teal" onClick={submit}>Save</Button>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
        </div>
      </div>
    </div>
  );
}

function HrEmployees({ onOpenProfile, onUpdateSalary }) {
  const [q, setQ] = useState("");
  const [salaryTarget, setSalaryTarget] = useState(null);
  const filtered = EMPLOYEES.filter((e) => (e.name + e.id + e.position + e.dept).toLowerCase().includes(q.toLowerCase()));
  return (
    <div>
      <SectionTitle sub="Manage employee profiles, levels and reporting lines">Employees</SectionTitle>
      <div style={{ display: "flex", gap: 10, marginBottom: 14, alignItems: "center" }}>
        <div style={{ position: "relative", flex: 1, maxWidth: 320 }}>
          <Search size={14} color={T.muted} style={{ position: "absolute", left: 10, top: 10 }} />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search employees…" style={{ ...inputStyle, paddingLeft: 30, maxWidth: 320 }} />
        </div>
        <Pill tone="muted">New signups appear here automatically</Pill>
      </div>
      <Card style={{ overflow: "hidden" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead><tr style={{ background: T.bg, textAlign: "left" }}>{["Employee ID", "Name", "Role", "Level", "Position", "Department", "Salary", ""].map((h) => <th key={h} style={{ padding: "10px 14px", fontSize: 11.5, color: T.muted, fontWeight: 700 }}>{h}</th>)}</tr></thead>
          <tbody>
            {filtered.map((e) => (
              <tr key={e.id} style={{ borderTop: `1px solid ${T.border}` }}>
                <td style={{ padding: "10px 14px", fontFamily: mono, fontSize: 12 }}>{e.id}</td>
                <td style={{ padding: "10px 14px", fontWeight: 600 }}>{e.name}</td>
                <td style={{ padding: "10px 14px" }}><RolePill role={e.role} /></td>
                <td style={{ padding: "10px 14px" }}>{e.level ? <Pill tone="teal">{e.level}</Pill> : <span style={{ color: T.muted }}>—</span>}</td>
                <td style={{ padding: "10px 14px", color: T.muted }}>{e.position}</td>
                <td style={{ padding: "10px 14px", color: T.muted }}>{e.dept}</td>
                <td style={{ padding: "10px 14px" }}>
                  <button onClick={() => setSalaryTarget(e)} style={{ background: "none", border: "none", cursor: "pointer", fontFamily: mono, color: e.salary > 0 ? T.text : T.amber, fontWeight: e.salary > 0 ? 400 : 700, textDecoration: "underline", textDecorationStyle: "dotted", textDecorationColor: T.muted }}>{e.salary > 0 ? money(e.salary) : "Not set"}</button>
                </td>
                <td style={{ padding: "10px 14px" }}><button onClick={() => onOpenProfile(e)} style={{ background: "none", border: "none", cursor: "pointer", color: T.teal }}><ChevronRight size={16} /></button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
      <EditSalaryModal employee={salaryTarget} onClose={() => setSalaryTarget(null)}
        onSave={(id, salary) => { onUpdateSalary(id, salary); setSalaryTarget(null); }} />
    </div>
  );
}

function HrPayroll({ payrollStage, setPayslipView, payrollRecords, resendPayslipEmail, payrollLoading, advanceStage }) {
  const hasRealRecords = API_BASE_URL && Object.keys(payrollRecords || {}).length > 0;
  const stageIdx = STAGES.indexOf(payrollStage);
  return (
    <div>
      <SectionTitle sub={`Reviewing variable earnings and deductions for ${CURRENT_MONTH}`}>Payroll — {CURRENT_MONTH}</SectionTitle>
      <div style={{ marginBottom: 14, display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
        <Pill tone="teal">Status: {payrollStage}</Pill>
        {payrollLoading && <span style={{ fontSize: 12, color: T.muted }}>Loading real payroll data…</span>}
        {API_BASE_URL && !payrollLoading && !hasRealRecords && <Pill tone="amber">Showing estimated figures — couldn't load real payroll data</Pill>}
        {advanceStage && (stageIdx < STAGES.length - 1
          ? <Button variant="teal" icon={ArrowRight} small onClick={advanceStage}>Advance to {STAGES[stageIdx + 1]}</Button>
          : <Pill tone="green">All payslips sent for {CURRENT_MONTH}</Pill>)}
      </div>
      <Card style={{ overflow: "hidden" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead><tr style={{ background: T.bg, textAlign: "left" }}>{["Employee", "Basic", "Overtime", "Bonus", "Gross", "Deductions", "Net Pay", ""].map((h) => <th key={h} style={{ padding: "10px 14px", fontSize: 11.5, color: T.muted, fontWeight: 700 }}>{h}</th>)}</tr></thead>
          <tbody>
            {EMPLOYEES.map((e) => {
              const real = hasRealRecords ? payrollRecords[e.id] : null;
              const f = real || calcPayroll(e, 3);
              return (
                <tr key={e.id} style={{ borderTop: `1px solid ${T.border}` }}>
                  <td style={{ padding: "10px 14px" }}><div style={{ fontWeight: 600 }}>{e.name}</div><div style={{ fontFamily: mono, fontSize: 11, color: T.muted }}>{e.id}</div></td>
                  <td style={{ padding: "10px 14px", fontFamily: mono }}>{money(f.basic)}</td>
                  <td style={{ padding: "10px 14px", fontFamily: mono, color: T.muted }}>{money(f.overtime)}</td>
                  <td style={{ padding: "10px 14px", fontFamily: mono, color: T.muted }}>{money(f.bonus)}</td>
                  <td style={{ padding: "10px 14px", fontFamily: mono, fontWeight: 600 }}>{money(f.gross)}</td>
                  <td style={{ padding: "10px 14px", fontFamily: mono, color: T.red }}>-{money(f.totalDeductions)}</td>
                  <td style={{ padding: "10px 14px", fontFamily: mono, fontWeight: 700, color: T.navy }}>{money(f.net)}</td>
                  <td style={{ padding: "10px 14px" }}>
                    <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
                      <button onClick={() => setPayslipView({ emp: e, month: CURRENT_MONTH, figures: f })} style={{ background: "none", border: "none", cursor: "pointer", color: T.teal }} title="Preview payslip"><Eye size={16} /></button>
                      <button onClick={() => downloadPayslipPdf(e, CURRENT_MONTH, f)} style={{ background: "none", border: "none", cursor: "pointer", color: T.muted }} title="Download PDF"><Download size={16} /></button>
                      {real && real.status === "SENT" && (
                        <button onClick={() => resendPayslipEmail(real._dbId)} title={real.emailSent ? "Resend email" : `Resend (last attempt failed: ${real.emailFailureReason || "unknown"})`} style={{ background: "none", border: "none", cursor: "pointer", color: real.emailSent ? T.green : T.red }}>
                          <Mail size={16} />
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </Card>
    </div>
  );
}

function HrLeave({ leaveRequests, decider, onDecide }) {
  const [target, setTarget] = useState(null);
  return (
    <div>
      <SectionTitle sub="Organization-wide visibility over leave applications">Leave Requests</SectionTitle>
      <Card style={{ overflow: "hidden" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead><tr style={{ background: T.bg, textAlign: "left" }}>{["Employee", "Type", "Dates", "Days", "Reason", "Status", ""].map((h) => <th key={h} style={{ padding: "10px 14px", fontSize: 11.5, color: T.muted, fontWeight: 700 }}>{h}</th>)}</tr></thead>
          <tbody>
            {leaveRequests.map((r) => {
              const e = empById(r.emp);
              return (
                <tr key={r.id} style={{ borderTop: `1px solid ${T.border}` }}>
                  <td style={{ padding: "10px 14px" }}>{e.name}</td>
                  <td style={{ padding: "10px 14px", color: T.muted }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                      {r.type}
                      {r.proofFileDataUrl && <FileCheck2 size={13} color={T.teal} style={{ cursor: "pointer", flexShrink: 0 }} onClick={() => viewProofDocument({ name: r.proofFileName, dataUrl: r.proofFileDataUrl })} />}
                    </div>
                  </td>
                  <td style={{ padding: "10px 14px", fontFamily: mono, fontSize: 12 }}>{r.start} → {r.end}</td>
                  <td style={{ padding: "10px 14px", fontFamily: mono }}>{r.days}</td>
                  <td style={{ padding: "10px 14px", color: T.muted }}>{r.reason}</td>
                  <td style={{ padding: "10px 14px" }}><StatusPill status={r.status} /></td>
                  <td style={{ padding: "10px 14px" }}>
                    {r.status === "Pending" ? (
                      <div style={{ display: "flex", gap: 6 }}>
                        <button onClick={() => setTarget({ request: r, intent: "approve" })} style={{ background: "none", border: "none", cursor: "pointer" }} title="Approve"><CheckCircle2 size={17} color={T.green} /></button>
                        <button onClick={() => setTarget({ request: r, intent: "reject" })} style={{ background: "none", border: "none", cursor: "pointer" }} title="Decline"><XCircle size={17} color={T.red} /></button>
                      </div>
                    ) : (
                      <button onClick={() => downloadLeaveLetter(r, e)} style={{ background: "none", border: "none", cursor: "pointer", color: T.teal }} title="Download signed letter"><Download size={16} /></button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </Card>
      <DecisionModal target={target} decider={decider} onClose={() => setTarget(null)}
        onConfirm={(id, approve, sig, reason) => { onDecide(id, approve, sig, reason); setTarget(null); }} />
    </div>
  );
}

/* ---------------------------------------------------------------------- */
/* ADMIN AREA                                                             */
/* ---------------------------------------------------------------------- */
function AdminOverview({ supportTickets, officeIssues }) {
  const roleCounts = ["master", "it_support", "admin", "hr", "manager", "employee"].map((r) => ({ role: r, count: EMPLOYEES.filter((e) => e.role === r).length }));
  const openTickets = supportTickets.filter((t) => t.status !== "Resolved").length;
  const openOfficeIssues = officeIssues.filter((i) => i.status !== "Resolved").length;
  return (
    <div>
      <SectionTitle sub="System-level status for the whole platform">System Overview</SectionTitle>
      <div style={{ display: "flex", gap: 14, flexWrap: "wrap", marginBottom: 22 }}>
        <StatCard icon={Users} label="User Accounts" value={EMPLOYEES.length} />
        <StatCard icon={Building2} label="Departments" value={DEPARTMENTS.length} />
        <StatCard icon={LifeBuoy} label="Open Support Tickets" value={openTickets} tone={openTickets > 0 ? T.amber : T.green} />
        <StatCard icon={MapPin} label="Open Office Issues" value={openOfficeIssues} tone={openOfficeIssues > 0 ? T.amber : T.green} />
      </div>
      <Card style={{ padding: 18 }}>
        <div style={{ fontSize: 13.5, fontWeight: 650, marginBottom: 14 }}>Accounts by Role</div>
        {roleCounts.map((r) => (
          <div key={r.role} style={{ marginBottom: 10 }}>
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12.5, marginBottom: 4 }}>
              <span style={{ color: T.muted }}>{ROLE_LABEL[r.role]}</span><span style={{ fontFamily: mono, fontWeight: 600 }}>{r.count}</span>
            </div>
            <div style={{ height: 6, background: "#EEF0F3", borderRadius: 3 }}><div style={{ height: 6, borderRadius: 3, background: T.purple, width: `${(r.count / EMPLOYEES.length) * 100}%` }} /></div>
          </div>
        ))}
      </Card>
    </div>
  );
}

function AdminCompanySettings() {
  const field = (label, value) => (
    <div style={{ marginBottom: 14 }}>
      <label style={{ fontSize: 11, fontWeight: 700, color: T.muted, textTransform: "uppercase", letterSpacing: 0.3 }}>{label}</label>
      <input readOnly value={value} style={{ ...inputStyle, marginTop: 5, background: T.bg }} />
    </div>
  );
  return (
    <div>
      <SectionTitle sub="Company profile and automated payslip delivery">Company & Settings</SectionTitle>
      <div style={{ display: "flex", gap: 20, flexWrap: "wrap" }}>
        <Card style={{ padding: 20, flex: "1 1 320px" }}>
          <div style={{ fontSize: 13.5, fontWeight: 650, marginBottom: 14 }}>Company Profile</div>
          {field("Company Name", COMPANY.name)}
          {field("Registration No.", COMPANY.regNo || "Not yet added — enter your CIPC registration number")}
          {field("Address", COMPANY.address)}
          {field("Email", COMPANY.email)}
          {field("Phone", COMPANY.phone)}
          {field("Website", COMPANY.website)}
          <Button variant="ghost" small>Save Changes</Button>
        </Card>
        <Card style={{ padding: 20, flex: "1 1 320px" }}>
          <div style={{ fontSize: 13.5, fontWeight: 650, marginBottom: 4 }}>Payslip Delivery</div>
          <div style={{ fontSize: 12, color: T.muted, marginBottom: 14 }}>Automatic monthly payslip generation and email delivery.</div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
            <span style={{ fontSize: 13 }}>Automatic Sending</span><Pill tone="green">ON</Pill>
          </div>
          <div style={{ fontSize: 12, color: T.muted, marginBottom: 6 }}>Delivery timing</div>
          <div style={{ display: "flex", flexDirection: "column", gap: 6, fontSize: 13, marginBottom: 14 }}>
            <label style={{ display: "flex", gap: 8, alignItems: "center" }}><input type="radio" checked readOnly /> Last day of month</label>
            <label style={{ display: "flex", gap: 8, alignItems: "center", color: T.muted }}><input type="radio" readOnly /> One day before month end</label>
          </div>
          {field("Delivery Time", "18:00")}
        </Card>
      </div>
    </div>
  );
}

function LevelRow({ level, onSave }) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState({ default: level.default, min: level.min, max: level.max });

  if (!editing) {
    return (
      <tr style={{ borderTop: `1px solid ${T.border}` }}>
        <td style={{ padding: "10px 14px", fontWeight: 600 }}>{level.name}</td>
        <td style={{ padding: "10px 14px", fontFamily: mono }}>{money(level.default)}</td>
        <td style={{ padding: "10px 14px", fontFamily: mono, color: T.muted }}>{money(level.min)} – {money(level.max)}</td>
        <td style={{ padding: "10px 14px" }}><button onClick={() => setEditing(true)} style={{ background: "none", border: "none", cursor: "pointer", color: T.teal, fontSize: 12.5, fontWeight: 600 }}>Edit</button></td>
      </tr>
    );
  }
  return (
    <tr style={{ borderTop: `1px solid ${T.border}`, background: T.tealLight }}>
      <td style={{ padding: "8px 14px", fontWeight: 600 }}>{level.name}</td>
      <td style={{ padding: "8px 14px" }}><input type="number" value={draft.default} onChange={(e) => setDraft({ ...draft, default: Number(e.target.value) })} style={{ ...inputStyle, padding: "5px 8px", width: 100 }} /></td>
      <td style={{ padding: "8px 14px" }}>
        <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
          <input type="number" value={draft.min} onChange={(e) => setDraft({ ...draft, min: Number(e.target.value) })} style={{ ...inputStyle, padding: "5px 8px", width: 90 }} />
          <span style={{ color: T.muted }}>–</span>
          <input type="number" value={draft.max} onChange={(e) => setDraft({ ...draft, max: Number(e.target.value) })} style={{ ...inputStyle, padding: "5px 8px", width: 90 }} />
        </div>
      </td>
      <td style={{ padding: "8px 14px" }}>
        <div style={{ display: "flex", gap: 8 }}>
          <button onClick={() => { onSave(level.name, draft); setEditing(false); }} style={{ background: "none", border: "none", cursor: "pointer", color: T.green, fontSize: 12.5, fontWeight: 600 }}>Save</button>
          <button onClick={() => { setDraft({ default: level.default, min: level.min, max: level.max }); setEditing(false); }} style={{ background: "none", border: "none", cursor: "pointer", color: T.muted, fontSize: 12.5 }}>Cancel</button>
        </div>
      </td>
    </tr>
  );
}

function AddLevelForm({ onAdd }) {
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ name: "", default: "", min: "", max: "" });
  const [error, setError] = useState("");

  const submit = () => {
    if (!form.name.trim()) { setError("Give the new level/role a name."); return; }
    if (LEVELS.some((l) => l.name.toLowerCase() === form.name.trim().toLowerCase())) { setError("A level with that name already exists."); return; }
    onAdd({ name: form.name.trim(), default: Number(form.default) || 0, min: Number(form.min) || 0, max: Number(form.max) || 0 });
    setForm({ name: "", default: "", min: "", max: "" });
    setError("");
    setOpen(false);
  };

  if (!open) return <Button variant="ghost" small onClick={() => setOpen(true)}>+ Add Level</Button>;

  return (
    <Card style={{ padding: 16, marginTop: 12, maxWidth: 460 }}>
      <div style={{ fontSize: 12.5, fontWeight: 700, marginBottom: 10 }}>New Level / Role</div>
      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} style={inputStyle} placeholder="Level name, e.g. Lead" />
        <div style={{ display: "flex", gap: 8 }}>
          <input type="number" value={form.default} onChange={(e) => setForm({ ...form, default: e.target.value })} style={inputStyle} placeholder="Default salary" />
          <input type="number" value={form.min} onChange={(e) => setForm({ ...form, min: e.target.value })} style={inputStyle} placeholder="Min" />
          <input type="number" value={form.max} onChange={(e) => setForm({ ...form, max: e.target.value })} style={inputStyle} placeholder="Max" />
        </div>
        {error && <div style={{ fontSize: 11.5, color: T.red }}>{error}</div>}
        <div style={{ display: "flex", gap: 8 }}>
          <Button variant="teal" small onClick={submit}>Add Level</Button>
          <Button variant="ghost" small onClick={() => setOpen(false)}>Cancel</Button>
        </div>
      </div>
    </Card>
  );
}

function AdminLevels({ onUpdateLevel, onAddLevel }) {
  return (
    <div>
      <SectionTitle sub="Set default salaries and ranges per level/role — used when generating payroll and as the starting salary for new signups">Salary Structure & Departments</SectionTitle>
      <div style={{ display: "flex", gap: 20, flexWrap: "wrap" }}>
        <div style={{ flex: "2 1 460px" }}>
          <Card style={{ overflow: "hidden" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
              <thead><tr style={{ background: T.bg, textAlign: "left" }}>{["Level", "Default Salary", "Range", ""].map((h) => <th key={h} style={{ padding: "10px 14px", fontSize: 11.5, color: T.muted, fontWeight: 700 }}>{h}</th>)}</tr></thead>
              <tbody>
                {LEVELS.map((l) => <LevelRow key={l.name} level={l} onSave={onUpdateLevel} />)}
              </tbody>
            </table>
          </Card>
          <div style={{ marginTop: 12 }}>
            <AddLevelForm onAdd={onAddLevel} />
          </div>
        </div>
        <Card style={{ padding: 18, flex: "1 1 220px" }}>
          <div style={{ fontSize: 13.5, fontWeight: 650, marginBottom: 12 }}>Departments</div>
          {DEPARTMENTS.map((d) => (
            <div key={d} style={{ display: "flex", justifyContent: "space-between", padding: "7px 0", borderBottom: `1px solid ${T.border}`, fontSize: 13 }}>
              <span>{d}</span><span style={{ fontFamily: mono, color: T.muted }}>{EMPLOYEES.filter((e) => e.dept === d).length}</span>
            </div>
          ))}
        </Card>
      </div>
    </div>
  );
}

const ASSIGNABLE_ROLES = ["employee", "manager", "hr", "admin", "it_support"];

function AdminUsers({ currentUserId, isMaster, onChangeRole, onRefresh }) {
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
        <SectionTitle sub={isMaster ? "Every account — you're the only one who can change roles" : "Every account and its assigned system role"}>User Accounts</SectionTitle>
        {isMaster && onRefresh && (
          <Button variant="ghost" small icon={RefreshCw} onClick={onRefresh}>Refresh (pulls in new signups)</Button>
        )}
      </div>
      <Card style={{ overflow: "hidden" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead><tr style={{ background: T.bg, textAlign: "left" }}>{["Employee ID", "Name", "Role", "Email", "Status", ""].map((h) => <th key={h} style={{ padding: "10px 14px", fontSize: 11.5, color: T.muted, fontWeight: 700 }}>{h}</th>)}</tr></thead>
          <tbody>
            {EMPLOYEES.map((e) => (
              <tr key={e.id} style={{ borderTop: `1px solid ${T.border}`, background: e.id === currentUserId ? T.tealLight : "transparent" }}>
                <td style={{ padding: "10px 14px", fontFamily: mono, fontSize: 12 }}>{e.id}</td>
                <td style={{ padding: "10px 14px", fontWeight: 600 }}>{e.name}{e.id === currentUserId && <span style={{ color: T.muted, fontWeight: 400 }}> (you)</span>}</td>
                <td style={{ padding: "10px 14px" }}>
                  {isMaster && e.role !== "master" && e.id !== currentUserId ? (
                    <select value={e.role} onChange={(ev) => onChangeRole(e.id, ev.target.value)} style={{ ...inputStyle, padding: "5px 8px", fontSize: 12, width: "auto" }}>
                      {ASSIGNABLE_ROLES.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
                    </select>
                  ) : (
                    <RolePill role={e.role} />
                  )}
                </td>
                <td style={{ padding: "10px 14px", color: T.muted }}>{e.email}</td>
                <td style={{ padding: "10px 14px" }}><Pill tone="green">Active</Pill></td>
                <td style={{ padding: "10px 14px" }}><KeyRound size={15} color={T.muted} style={{ cursor: "pointer" }} title="Reset password" /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </div>
  );
}

/* ---------------------------------------------------------------------- */
/* MANAGER VIEW                                                           */
/* ---------------------------------------------------------------------- */
function ManagerView({ manager, leaveRequests, onDecide, allEmployees }) {
  const [target, setTarget] = useState(null);
  const team = allEmployees.filter((e) => e.manager === manager.id);
  const teamIds = team.map((e) => e.id);
  const teamRequests = leaveRequests.filter((r) => teamIds.includes(r.emp));
  const pending = teamRequests.filter((r) => r.status === "Pending");
  return (
    <div>
      <SectionTitle sub={`Signed in as ${manager.name} · ${manager.position}`}>My Team</SectionTitle>
      <div style={{ display: "flex", gap: 14, flexWrap: "wrap", marginBottom: 22 }}>
        <StatCard icon={Users} label="Team Members" value={team.length} />
        <StatCard icon={Clock} label="Pending Approvals" value={pending.length} tone={T.amber} />
      </div>
      <SectionTitle>Team Members</SectionTitle>
      <Card style={{ overflow: "hidden", marginBottom: 22 }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead><tr style={{ background: T.bg, textAlign: "left" }}>{["Employee", "Level", "Position", "Leave Balance (Annual)"].map((h) => <th key={h} style={{ padding: "10px 14px", fontSize: 11.5, color: T.muted, fontWeight: 700 }}>{h}</th>)}</tr></thead>
          <tbody>
            {team.map((e) => (
              <tr key={e.id} style={{ borderTop: `1px solid ${T.border}` }}>
                <td style={{ padding: "10px 14px" }}>{e.name}</td>
                <td style={{ padding: "10px 14px" }}>{e.level ? <Pill tone="teal">{e.level}</Pill> : <span style={{ color: T.muted }}>—</span>}</td>
                <td style={{ padding: "10px 14px", color: T.muted }}>{e.position}</td>
                <td style={{ padding: "10px 14px", fontFamily: mono }}>{(LEAVE_BALANCES[e.id] || {})["Annual Leave"] ?? "—"} days</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
      <SectionTitle sub="Requests from employees reporting to you">Leave Requests To Review</SectionTitle>
      <Card style={{ overflow: "hidden" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead><tr style={{ background: T.bg, textAlign: "left" }}>{["Employee", "Type", "Dates", "Days", "Status", ""].map((h) => <th key={h} style={{ padding: "10px 14px", fontSize: 11.5, color: T.muted, fontWeight: 700 }}>{h}</th>)}</tr></thead>
          <tbody>
            {teamRequests.length === 0 && <tr><td colSpan={6} style={{ padding: 18, color: T.muted, textAlign: "center" }}>No leave requests from your team.</td></tr>}
            {teamRequests.map((r) => {
              const e = empById(r.emp);
              return (
                <tr key={r.id} style={{ borderTop: `1px solid ${T.border}` }}>
                  <td style={{ padding: "10px 14px" }}>{e.name}</td>
                  <td style={{ padding: "10px 14px", color: T.muted }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                      {r.type}
                      {r.proofFileDataUrl && <FileCheck2 size={13} color={T.teal} style={{ cursor: "pointer", flexShrink: 0 }} onClick={() => viewProofDocument({ name: r.proofFileName, dataUrl: r.proofFileDataUrl })} />}
                    </div>
                  </td>
                  <td style={{ padding: "10px 14px", fontFamily: mono, fontSize: 12 }}>{r.start} → {r.end}</td>
                  <td style={{ padding: "10px 14px", fontFamily: mono }}>{r.days}</td>
                  <td style={{ padding: "10px 14px" }}><StatusPill status={r.status} /></td>
                  <td style={{ padding: "10px 14px" }}>
                    {r.status === "Pending" ? (
                      <div style={{ display: "flex", gap: 6 }}>
                        <Button variant="success" small icon={CheckCircle2} onClick={() => setTarget({ request: r, intent: "approve" })}>Approve</Button>
                        <Button variant="danger" small icon={XCircle} onClick={() => setTarget({ request: r, intent: "reject" })}>Reject</Button>
                      </div>
                    ) : (
                      <button onClick={() => downloadLeaveLetter(r, e)} style={{ background: "none", border: "none", cursor: "pointer", color: T.teal }} title="Download signed letter"><Download size={16} /></button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </Card>
      <DecisionModal target={target} decider={manager} onClose={() => setTarget(null)}
        onConfirm={(id, approve, sig, reason) => { onDecide(id, approve, sig, reason); setTarget(null); }} />
    </div>
  );
}

/* ---------------------------------------------------------------------- */
/* EMPLOYEE VIEW (also used as self-service for HR / Admin / Manager)     */
/* ---------------------------------------------------------------------- */
/* IT ASSISTANT — keyword-matched chatbot over IT_FAQ, sourced from       */
/* K and K Media's IT Operations Documentation plus a few general tips    */
/* ---------------------------------------------------------------------- */
function ITAssistantChat() {
  const [messages, setMessages] = useState([
    { from: "bot", text: "Hi! I can help with common IT issues — Outlook, Teams, printers, email setup, WiFi, and more. Try asking, or use the quick suggestions below." },
  ]);
  const [input, setInput] = useState("");
  const listRef = useRef(null);

  useEffect(() => {
    if (listRef.current) listRef.current.scrollTop = listRef.current.scrollHeight;
  }, [messages]);

  const findAnswer = (text) => {
    const lower = text.toLowerCase();
    const match = IT_FAQ.find((entry) => {
      const mustOk = entry.must.every((m) => lower.includes(m));
      const anyOk = entry.any.length === 0 || entry.any.some((a) => lower.includes(a));
      return mustOk && anyOk;
    });
    return match ? match.answer : "I don't have guidance for that yet. Please log a Support or Office Issue ticket and IT support will help you directly.";
  };

  const send = (text) => {
    if (!text.trim()) return;
    setMessages((m) => [...m, { from: "user", text }, { from: "bot", text: findAnswer(text) }]);
    setInput("");
  };

  return (
    <Card style={{ padding: 0, maxWidth: 540, overflow: "hidden" }}>
      <div style={{ background: T.navy, padding: "12px 16px", display: "flex", alignItems: "center", gap: 8 }}>
        <Bot size={16} color="#fff" />
        <span style={{ color: "#fff", fontWeight: 700, fontSize: 13.5 }}>IT Assistant</span>
      </div>
      <div ref={listRef} style={{ padding: 16, height: 300, overflowY: "auto", display: "flex", flexDirection: "column", gap: 10 }}>
        {messages.map((m, i) => (
          <div key={i} style={{
            alignSelf: m.from === "user" ? "flex-end" : "flex-start",
            background: m.from === "user" ? T.navy : T.bg, color: m.from === "user" ? "#fff" : T.text,
            padding: "8px 12px", borderRadius: 10, maxWidth: "82%", fontSize: 13, lineHeight: 1.5,
          }}>{m.text}</div>
        ))}
      </div>
      <div style={{ padding: "10px 12px", borderTop: `1px solid ${T.border}`, display: "flex", gap: 6, flexWrap: "wrap" }}>
        {["Outlook frozen", "Teams won't load", "Printer not working", "Set up email"].map((q) => (
          <button key={q} onClick={() => send(q)} style={{ fontSize: 11, padding: "4px 10px", borderRadius: 14, border: `1px solid ${T.border}`, background: "#fff", cursor: "pointer", color: T.muted }}>{q}</button>
        ))}
      </div>
      <div style={{ padding: 12, borderTop: `1px solid ${T.border}`, display: "flex", gap: 8 }}>
        <input value={input} onChange={(e) => setInput(e.target.value)} onKeyDown={(e) => { if (e.key === "Enter") send(input); }} style={{ ...inputStyle, flex: 1 }} placeholder="Describe your issue…" />
        <Button variant="teal" small onClick={() => send(input)}>Send</Button>
      </div>
    </Card>
  );
}

/* ---------------------------------------------------------------------- */
/* PORTAL CHOOSER — first thing shown after login (for the employee-      */
/* facing side): choose Payroll & Leave, or IT Support                    */
/* ---------------------------------------------------------------------- */
function ChoicePortalCard({ icon: Icon, title, desc, onClick }) {
  return (
    <button onClick={onClick} style={{
      flex: "1 1 240px", textAlign: "left", background: T.surface, border: `1px solid ${T.border}`,
      borderRadius: 10, padding: 22, cursor: "pointer", display: "flex", flexDirection: "column", gap: 10,
    }}>
      <div style={{ width: 38, height: 38, borderRadius: 8, background: T.tealLight, display: "flex", alignItems: "center", justifyContent: "center" }}>
        <Icon size={19} color={T.teal} />
      </div>
      <div style={{ fontSize: 15, fontWeight: 700, color: T.text }}>{title}</div>
      <div style={{ fontSize: 12.5, color: T.muted, lineHeight: 1.5 }}>{desc}</div>
      <div style={{ fontSize: 12.5, color: T.teal, fontWeight: 600, marginTop: 4 }}>Continue →</div>
    </button>
  );
}

function PortalChooser({ empName, onChoose }) {
  return (
    <div style={{ maxWidth: 640 }}>
      <SectionTitle sub={`Welcome, ${empName}. What would you like to do?`}>Choose a Portal</SectionTitle>
      <div style={{ display: "flex", gap: 16, flexWrap: "wrap" }}>
        <ChoicePortalCard icon={Banknote} title="Payroll & Leave" desc="View your payslips, apply for leave, and check your leave balance." onClick={() => onChoose("leave")} />
        <ChoicePortalCard icon={LifeBuoy} title="IT Support" desc="Ask the assistant, or log a system or office issue." onClick={() => onChoose("itSupport")} />
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------------- */
/* IT SUPPORT PORTAL — assistant + entry points into Support/Office Issues */
/* ---------------------------------------------------------------------- */
function ITSupportPortal({ goSupport, goOfficeIssues }) {
  const [tab, setTab] = useState("assistant");
  const tabs = [
    { id: "assistant", label: "Ask the Assistant" },
    { id: "system", label: "Report a System Issue" },
    { id: "office", label: "Report an Office Issue" },
  ];
  return (
    <div>
      <SectionTitle sub="Ask the assistant for a quick fix, or log a ticket directly">IT Support</SectionTitle>
      <div style={{ display: "flex", gap: 6, marginBottom: 20, borderBottom: `1px solid ${T.border}` }}>
        {tabs.map((t) => (
          <button key={t.id} onClick={() => (t.id === "system" ? goSupport() : t.id === "office" ? goOfficeIssues() : setTab(t.id))} style={{
            background: "none", border: "none", cursor: "pointer", padding: "8px 4px", fontSize: 13, fontWeight: 600,
            color: tab === t.id ? T.navy : T.muted, borderBottom: tab === t.id ? `2px solid ${T.teal}` : "2px solid transparent",
          }}>{t.label}</button>
        ))}
      </div>
      {tab === "assistant" && <ITAssistantChat />}
    </div>
  );
}


function EmployeeView({ emp, leaveRequests, addLeaveRequest, history, setPayslipView, onBackToChooser }) {
  const [tab, setTab] = useState("dashboard");
  const [form, setForm] = useState({ type: LEAVE_TYPES[0], start: "", end: "", reason: "", signature: null, proofFile: null });
  const [formError, setFormError] = useState("");
  const balances = LEAVE_BALANCES[emp.id] || { "Annual Leave": 15, "Sick Leave": 10, "Family Responsibility Leave": 3 };
  const myRequests = leaveRequests.filter((r) => r.emp === emp.id);
  const myHistory = history[emp.id] || [];

  const days = (s, e) => { if (!s || !e) return 0; const d = (new Date(e) - new Date(s)) / 86400000 + 1; return d > 0 ? Math.round(d) : 0; };
  const proofRequired = PROOF_REQUIRED_TYPES.includes(form.type);

  const submit = () => {
    if (!form.start || !form.end || !form.reason) { setFormError("Please fill in the dates and reason."); return; }
    if (proofRequired && !form.proofFile) { setFormError(`${form.type} requires supporting proof — please attach a PDF or image.`); return; }
    if (!form.signature) { setFormError("Please sign the application before submitting."); return; }
    setFormError("");
    addLeaveRequest({
      id: `LR-${Math.floor(rand(myRequests.length + 500) * 900 + 100)}`, emp: emp.id, type: form.type, start: form.start, end: form.end,
      days: days(form.start, form.end), reason: form.reason, status: "Pending",
      employeeSignature: form.signature, employeeSignedAt: new Date().toISOString(),
      proofFileName: form.proofFile?.name || null, proofFileType: form.proofFile?.type || null, proofFileDataUrl: form.proofFile?.dataUrl || null,
    });
    setForm({ type: LEAVE_TYPES[0], start: "", end: "", reason: "", signature: null, proofFile: null });
    setTab("leaveHistory");
  };

  const tabs = [
    { id: "dashboard", label: "Dashboard" }, { id: "payslips", label: "My Payslips" },
    { id: "applyLeave", label: "Apply for Leave" }, { id: "leaveHistory", label: "Leave History" },
  ];

  return (
    <div>
      {onBackToChooser && (
        <button onClick={onBackToChooser} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "none", color: T.muted, fontSize: 13, fontWeight: 600, cursor: "pointer", padding: 0, marginBottom: 14 }}>
          <ArrowLeft size={15} /> Back
        </button>
      )}
      <SectionTitle sub={emp.role === "employee" ? `Signed in as ${emp.name} · ${emp.position}` : `Personal self-service · ${emp.name} (${ROLE_LABEL[emp.role]})`}>
        {emp.role === "employee" ? "Employee Dashboard" : "My Profile"}
      </SectionTitle>
      <div style={{ display: "flex", gap: 6, marginBottom: 20, borderBottom: `1px solid ${T.border}` }}>
        {tabs.map((t) => (
          <button key={t.id} onClick={() => setTab(t.id)} style={{ background: "none", border: "none", cursor: "pointer", padding: "8px 4px", fontSize: 13, fontWeight: 600, color: tab === t.id ? T.navy : T.muted, borderBottom: tab === t.id ? `2px solid ${T.teal}` : "2px solid transparent" }}>{t.label}</button>
        ))}
      </div>

      {tab === "dashboard" && (
        <div>
          <div style={{ display: "flex", gap: 14, flexWrap: "wrap", marginBottom: 22 }}>
            {Object.entries(balances).map(([k, v]) => <StatCard key={k} icon={CalendarDays} label={k} value={`${v}d`} title={LEAVE_POLICY[k]} />)}
          </div>
          <Card style={{ padding: 18 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}><Bell size={15} color={T.teal} /><span style={{ fontWeight: 650, fontSize: 13.5 }}>Notifications</span></div>
            <ul style={{ margin: "10px 0 0", paddingLeft: 18, fontSize: 13, color: T.muted, lineHeight: 1.9 }}>
              {myHistory.length > 0 && <li>Your {myHistory[myHistory.length - 1].month} payslip has been emailed to {emp.email}</li>}
              {myRequests.filter((r) => r.status !== "Pending").slice(-1).map((r) => (
                <li key={r.id}>Your {r.type} request ({r.start} – {r.end}) was <strong style={{ color: r.status === "Approved" ? T.green : T.red }}>{r.status.toLowerCase()}</strong></li>
              ))}
              <li>Leave balance updated for the new leave cycle</li>
            </ul>
          </Card>
        </div>
      )}

      {tab === "payslips" && (
        myHistory.length === 0 ? (
          <Card style={{ padding: 24, textAlign: "center", color: T.muted, fontSize: 13 }}>No payslips yet — these appear once the first payroll run after you join has been sent.</Card>
        ) : (
          <Card style={{ overflow: "hidden" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
              <thead><tr style={{ background: T.bg, textAlign: "left" }}>{["Pay Period", "Net Pay", ""].map((h) => <th key={h} style={{ padding: "10px 14px", fontSize: 11.5, color: T.muted, fontWeight: 700 }}>{h}</th>)}</tr></thead>
              <tbody>
                {myHistory.map((h) => (
                  <tr key={h.month} style={{ borderTop: `1px solid ${T.border}` }}>
                    <td style={{ padding: "10px 14px" }}>{h.month}</td>
                    <td style={{ padding: "10px 14px", fontFamily: mono, fontWeight: 600 }}>{money(h.figures.net)}</td>
                    <td style={{ padding: "10px 14px" }}>
                      <div style={{ display: "flex", gap: 10 }}>
                        <button onClick={() => setPayslipView({ emp, month: h.month, figures: h.figures })} style={{ background: "none", border: "none", cursor: "pointer", color: T.teal }} title="Preview"><Eye size={16} /></button>
                        <button onClick={() => downloadPayslipPdf(emp, h.month, h.figures)} style={{ background: "none", border: "none", cursor: "pointer", color: T.muted }} title="Download PDF"><Download size={16} /></button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>
        )
      )}

      {tab === "applyLeave" && (
        <Card style={{ padding: 22, maxWidth: 460 }}>
          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            <div>
              <label style={{ fontSize: 12, fontWeight: 700, color: T.muted }}>Leave Type</label>
              <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value, proofFile: null })} style={{ ...inputStyle, marginTop: 5 }}>{LEAVE_TYPES.map((t) => <option key={t}>{t}</option>)}</select>
              {LEAVE_POLICY[form.type] && (
                <div style={{ marginTop: 6, fontSize: 11.5, color: T.muted, background: T.bg, padding: "8px 10px", borderRadius: 6, lineHeight: 1.5 }}>
                  {LEAVE_POLICY[form.type]}
                </div>
              )}
              {proofRequired && <div style={{ marginTop: 5 }}><Pill tone="amber">Proof required for this leave type</Pill></div>}
            </div>
            <div style={{ display: "flex", gap: 10 }}>
              <div style={{ flex: 1 }}><label style={{ fontSize: 12, fontWeight: 700, color: T.muted }}>Start Date</label><input type="date" value={form.start} onChange={(e) => setForm({ ...form, start: e.target.value })} style={{ ...inputStyle, marginTop: 5 }} /></div>
              <div style={{ flex: 1 }}><label style={{ fontSize: 12, fontWeight: 700, color: T.muted }}>End Date</label><input type="date" value={form.end} onChange={(e) => setForm({ ...form, end: e.target.value })} style={{ ...inputStyle, marginTop: 5 }} /></div>
            </div>
            <div>
              <label style={{ fontSize: 12, fontWeight: 700, color: T.muted }}>Reason</label>
              <textarea value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} rows={3} style={{ ...inputStyle, marginTop: 5, resize: "vertical" }} />
            </div>
            <div style={{ fontSize: 12.5, color: T.muted }}>Days requested: <strong style={{ fontFamily: mono }}>{days(form.start, form.end)}</strong></div>
            <ProofUpload value={form.proofFile} onChange={(f) => setForm({ ...form, proofFile: f })} required={proofRequired} />
            <SignaturePad value={form.signature} onChange={(sig) => setForm({ ...form, signature: sig })} />
            {emp.role !== "employee" && (
              <div style={{ fontSize: 11.5, color: T.muted, background: T.bg, padding: "8px 10px", borderRadius: 6 }}>This request will go to HR for approval, the same as any other employee's leave application.</div>
            )}
            {formError && (
              <div style={{ display: "flex", gap: 6, alignItems: "center", color: T.red, background: T.redBg, padding: "8px 10px", borderRadius: 6, fontSize: 12.5 }}>
                <AlertCircle size={14} /> {formError}
              </div>
            )}
            <Button variant="teal" onClick={submit}>Submit Application</Button>
          </div>
        </Card>
      )}

      {tab === "leaveHistory" && (
        <Card style={{ overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead><tr style={{ background: T.bg, textAlign: "left" }}>{["Type", "Dates", "Days", "Reason", "Status", ""].map((h) => <th key={h} style={{ padding: "10px 14px", fontSize: 11.5, color: T.muted, fontWeight: 700 }}>{h}</th>)}</tr></thead>
            <tbody>
              {myRequests.length === 0 && <tr><td colSpan={6} style={{ padding: 18, textAlign: "center", color: T.muted }}>No leave history yet.</td></tr>}
              {myRequests.map((r) => (
                <tr key={r.id} style={{ borderTop: `1px solid ${T.border}` }}>
                  <td style={{ padding: "10px 14px" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                      {r.type}
                      {r.proofFileDataUrl && <FileCheck2 size={13} color={T.teal} style={{ cursor: "pointer", flexShrink: 0 }} onClick={() => viewProofDocument({ name: r.proofFileName, dataUrl: r.proofFileDataUrl })} />}
                    </div>
                  </td>
                  <td style={{ padding: "10px 14px", fontFamily: mono, fontSize: 12 }}>{r.start} → {r.end}</td>
                  <td style={{ padding: "10px 14px", fontFamily: mono }}>{r.days}</td>
                  <td style={{ padding: "10px 14px", color: T.muted }}>{r.reason}</td>
                  <td style={{ padding: "10px 14px" }}><StatusPill status={r.status} /></td>
                  <td style={{ padding: "10px 14px" }}>
                    {r.status !== "Pending" && (
                      <button onClick={() => downloadLeaveLetter(r, emp)} style={{ background: "none", border: "none", cursor: "pointer", color: T.teal }} title="Download signed letter"><Download size={16} /></button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </div>
  );
}

/* ---------------------------------------------------------------------- */
/* APP SHELL                                                              */
/* ---------------------------------------------------------------------- */
export default function App() {
  const [screen, setScreen] = useState("login"); // "login" | "signup" | "app"
  const [currentUserId, setCurrentUserId] = useState(null);
  const [portalChoice, setPortalChoice] = useState(null); // null | "leave" | "itSupport"
  const [appUserIdByEmployeeCode, setAppUserIdByEmployeeCode] = useState({});
  const [employeesState, setEmployeesState] = useState(EMPLOYEES);
  const [balancesState, setBalancesState] = useState(LEAVE_BALANCES);
  const [levelsState, setLevelsState] = useState(LEVELS);
  const [viewMode, setViewMode] = useState("role"); // "role" | "selfService" | "settings"
  const [hrTab, setHrTab] = useState("dashboard");
  const [adminTab, setAdminTab] = useState("overview");
  const [itSupportTab, setItSupportTab] = useState("officeIssues");
  const [masterTab, setMasterTab] = useState("users");
  const [leaveRequests, setLeaveRequests] = useState(INITIAL_LEAVE_REQUESTS);
  const [supportTickets, setSupportTickets] = useState([]);
  const [officeIssues, setOfficeIssues] = useState([]);
  const [officeAvailability, setOfficeAvailability] = useState("");
  const [payrollStage, setPayrollStage] = useState("DRAFT");
  const [payrollRecords, setPayrollRecords] = useState({}); // employeeCode -> mapped Payroll row (real backend data)
  const [payrollLoading, setPayrollLoading] = useState(false);
  const [profileEmp, setProfileEmp] = useState(null);
  const [payslipView, setPayslipView] = useState(null);

  // Sync the module-level mutable data with React state every render, so
  // every component below always reads the latest signups / profile edits.
  EMPLOYEES = employeesState;
  LEAVE_BALANCES = balancesState;
  LEVELS = levelsState;

  const history = useMemo(() => buildHistory(employeesState.filter((e) => PAST_MONTHS && e.start <= "2026-06-01")), [employeesState]);

  const fetchPayrollForPeriod = async (employeeList) => {
    if (!API_BASE_URL) return;
    const list = employeeList || employeesState;
    setPayrollLoading(true);
    try {
      let rows = await apiFetch(`/api/hr/payroll?payPeriod=${CURRENT_PAY_PERIOD}`);
      const haveCodes = new Set(rows.map((r) => r.employee?.employeeCode).filter(Boolean));
      const missing = list.filter((e) => e._dbId && !haveCodes.has(e.id));
      for (const emp of missing) {
        try {
          const created = await apiFetch(`/api/hr/payroll/${emp._dbId}/draft`, { method: "POST" });
          rows.push(created);
        } catch (e) { /* one employee's draft failing shouldn't block the rest */ }
      }
      const mapped = {};
      rows.forEach((r) => { const m = mapBackendPayroll(r); if (m.empId) mapped[m.empId] = m; });
      setPayrollRecords(mapped);
      if (rows.length > 0) setPayrollStage(rows[0].status);
    } catch (e) {
      // non-fatal — HrPayroll/HrDashboard fall back to local calcPayroll figures
    }
    setPayrollLoading(false);
  };

  const advanceStage = async () => {
    if (!API_BASE_URL) {
      const i = STAGES.indexOf(payrollStage);
      if (i < STAGES.length - 1) setPayrollStage(STAGES[i + 1]);
      return;
    }
    try {
      const rows = await apiFetch(`/api/hr/payroll/advance?payPeriod=${CURRENT_PAY_PERIOD}`, { method: "POST" });
      const mapped = {};
      rows.forEach((r) => { const m = mapBackendPayroll(r); if (m.empId) mapped[m.empId] = m; });
      setPayrollRecords(mapped);
      if (rows.length > 0) setPayrollStage(rows[0].status);
    } catch (e) {
      alert(`Couldn't advance payroll: ${e.message}`);
    }
  };

  const resendPayslipEmail = async (payrollDbId) => {
    if (!API_BASE_URL || !payrollDbId) return;
    try {
      const updated = await apiFetch(`/api/hr/payroll/${payrollDbId}/resend-email`, { method: "POST" });
      const m = mapBackendPayroll(updated);
      setPayrollRecords((pr) => ({ ...pr, [m.empId]: m }));
      alert(m.emailSent ? "Payslip email sent successfully." : `Send failed: ${m.emailFailureReason || "unknown reason"}`);
    } catch (e) {
      alert(`Couldn't resend: ${e.message}`);
    }
  };

  const fetchLeaveForRole = async (r, employeeId) => {
    if (!API_BASE_URL) return;
    try {
      let rows = [];
      if (["hr", "admin", "master", "it_support"].includes(r)) {
        rows = await apiFetch("/api/hr/leave");
      } else if (r === "manager") {
        rows = await apiFetch("/api/manager/team-leave");
      }
      const own = await apiFetch("/api/me/leave");
      const merged = new Map();
      [...rows, ...own].forEach((lr) => merged.set(lr.id, mapBackendLeaveRequest(lr)));
      setLeaveRequests((existing) => {
        const byId = new Map(existing.map((r2) => [r2.id, r2]));
        merged.forEach((v, k) => byId.set(`LR-${k}`, v));
        return Array.from(byId.values());
      });
    } catch (e) { /* non-fatal — leave screens fall back to local-only data */ }
    try {
      const balances = await apiFetch("/api/me/leave-balance");
      const mapped = {};
      balances.forEach((b) => { if (b.leaveType?.name) mapped[b.leaveType.name] = b.daysRemaining; });
      if (Object.keys(mapped).length > 0 && employeeId) setBalancesState((bs) => ({ ...bs, [employeeId]: mapped }));
    } catch (e) { /* non-fatal — falls back to default local balances */ }
  };

  const refreshAppUsers = async () => {
    if (!API_BASE_URL) return;
    try {
      const users = await apiFetch("/api/admin/users");
      const map = {};
      const roleByCode = {};
      users.forEach((u) => {
        if (u.employee?.employeeCode) {
          map[u.employee.employeeCode] = u.id;
          roleByCode[u.employee.employeeCode] = (u.role || "employee").toLowerCase();
        }
      });
      setAppUserIdByEmployeeCode(map);
      setEmployeesState((es) => es.map((e) => (roleByCode[e.id] ? { ...e, role: roleByCode[e.id] } : e)));
    } catch (e) { /* non-fatal — role changes will just fail with a clear error if attempted */ }
  };

  const handleLogin = async (email, password) => {
    if (!API_BASE_URL) {
      const user = employeesState.find((e) => e.email.toLowerCase() === email.toLowerCase());
      if (!user) return "No account found with that email address.";
      const expected = user.password || DEFAULT_PASSWORD;
      if (password !== expected) return "Incorrect password.";
      setCurrentUserId(user.id);
      setViewMode("role");
      setPortalChoice(null);
      setScreen("app");
      return null;
    }
    try {
      const auth = await apiFetch("/api/auth/login", { method: "POST", body: JSON.stringify({ email, password }) });
      setStoredToken(auth.token);
      const be = await apiFetch("/api/me");
      const mapped = { ...mapBackendEmployee(be), role: (auth.role || "employee").toLowerCase() };
      setEmployeesState((es) => (es.some((e) => e.id === mapped.id) ? es.map((e) => (e.id === mapped.id ? mapped : e)) : [...es, mapped]));
      if (["hr", "admin", "master", "it_support"].includes(mapped.role)) {
        try {
          const list = await apiFetch("/api/hr/employees");
          const mappedList = list.map(mapBackendEmployee);
          let roleByCode = {};
          try { roleByCode = await apiFetch("/api/hr/employee-roles"); } catch (e) { /* non-fatal — roles just won't display for others */ }
          const withRoles = mappedList.map((m) => (roleByCode[m.id] ? { ...m, role: roleByCode[m.id].toLowerCase() } : m));
          setEmployeesState((es) => {
            const byId = new Map(es.map((e) => [e.id, e]));
            withRoles.forEach((m) => byId.set(m.id, m));
            return Array.from(byId.values());
          });
          fetchPayrollForPeriod(mappedList);
        } catch (e) { /* non-fatal — HR/Admin screens fall back to whatever's already known locally */ }
      }
      if (mapped.role === "manager") {
        try {
          const team = await apiFetch("/api/manager/team");
          const mappedTeam = team.map(mapBackendEmployee);
          setEmployeesState((es) => {
            const byId = new Map(es.map((e) => [e.id, e]));
            mappedTeam.forEach((m) => byId.set(m.id, m));
            return Array.from(byId.values());
          });
        } catch (e) { /* non-fatal — team screen falls back to whatever's already known locally */ }
      }
      if (mapped.role === "master") {
        await refreshAppUsers();
      }
      fetchLeaveForRole(mapped.role, mapped.id);
      setCurrentUserId(mapped.id);
      setViewMode("role");
      setPortalChoice(null);
      setScreen("app");
      return null;
    } catch (e) {
      return e.message;
    }
  };

  const handleSignup = async (form) => {
    if (!API_BASE_URL) {
      const id = nextEmployeeId();
      const infoKeys = PERSONAL_INFO_GROUPS.flatMap((g) => g.fields.map(([key]) => key));
      const onboardingInfo = Object.fromEntries(infoKeys.map((k) => [k, form[k] || ""]));
      const newEmp = {
        id, name: form.name, role: form.role, level: null, dept: form.dept,
        position: form.position || (form.role === "hr" ? "HR Officer" : form.role === "admin" ? "System Administrator" : form.role === "it_support" ? "IT Support" : "Employee"),
        salary: 0, manager: null, start: "2026-09-10", email: form.email, phone: form.phone, password: form.password,
        office: form.office || OFFICES[0],
        agreedToTerms: true, termsAgreedAt: new Date().toISOString(), onboardingSignature: form.signature,
        ...onboardingInfo,
      };
      setEmployeesState((es) => [...es, newEmp]);
      setBalancesState((bs) => ({ ...bs, [id]: { "Annual Leave": 15, "Sick Leave": 10, "Family Responsibility Leave": 3 } }));
      setCurrentUserId(id);
      setViewMode("role");
      setPortalChoice(null);
      setScreen("app");
      return null;
    }
    const [firstName, ...rest] = form.name.trim().split(/\s+/);
    const lastName = rest.join(" ") || firstName;
    const infoKeys = PERSONAL_INFO_GROUPS.flatMap((g) => g.fields.map(([key]) => key));
    const payload = {
      firstName, lastName, email: form.email, password: form.password,
      phone: form.phone, position: form.position, department: form.dept, office: form.office,
      agreedToTerms: true, signature: form.signature,
      ...Object.fromEntries(infoKeys.map((k) => [k, form[k] || null])),
    };
    try {
      const auth = await apiFetch("/api/auth/signup", { method: "POST", body: JSON.stringify(payload) });
      setStoredToken(auth.token);
      const be = await apiFetch("/api/me");
      const mapped = { ...mapBackendEmployee(be), role: (auth.role || "employee").toLowerCase() };
      setEmployeesState((es) => [...es, mapped]);
      fetchLeaveForRole(mapped.role, mapped.id);
      setCurrentUserId(mapped.id);
      setViewMode("role");
      setPortalChoice(null);
      setScreen("app");
      return null;
    } catch (e) {
      return e.message;
    }
  };

  const handleLogout = () => { setStoredToken(null); setCurrentUserId(null); setScreen("login"); setViewMode("role"); setPortalChoice(null); };

  const handleSaveProfile = async (fields) => {
    const payload = { ...fields };
    if (payload.name) {
      const [fn, ...rest] = payload.name.trim().split(/\s+/);
      payload.firstName = fn;
      payload.lastName = rest.join(" ") || fn;
      delete payload.name;
    }
    // Email and department changes aren't part of self-service profile
    // updates on the backend yet — those still only update local state.
    delete payload.email;
    delete payload.dept;
    if (API_BASE_URL) {
      try {
        await apiFetch("/api/me/profile", { method: "PUT", body: JSON.stringify(payload) });
      } catch (e) {
        alert(`Couldn't save: ${e.message}`);
        return;
      }
    }
    setEmployeesState((es) => es.map((e) => (e.id === currentUserId ? { ...e, ...fields } : e)));
  };
  const handleChangePassword = (newPassword) => {
    setEmployeesState((es) => es.map((e) => (e.id === currentUserId ? { ...e, password: newPassword } : e)));
  };
  const updateLevel = (name, updates) =>
    setLevelsState((ls) => ls.map((l) => (l.name === name ? { ...l, ...updates } : l)));
  const addLevel = (level) => setLevelsState((ls) => [...ls, level]);
  const updateEmployeeSalary = async (employeeId, newSalary) => {
    if (API_BASE_URL) {
      const target = employeesState.find((e) => e.id === employeeId);
      if (target && target._dbId) {
        try {
          await apiFetch(`/api/hr/employees/${target._dbId}/profile`, { method: "PUT", body: JSON.stringify({ salary: newSalary }) });
        } catch (e) {
          alert(`Couldn't save the salary: ${e.message}`);
          return;
        }
      }
    }
    setEmployeesState((es) => es.map((e) => (e.id === employeeId ? { ...e, salary: newSalary } : e)));
  };
  const updateEmployeeManager = async (employeeId, managerId) => {
    if (API_BASE_URL) {
      const target = employeesState.find((e) => e.id === employeeId);
      if (target && target._dbId) {
        try {
          await apiFetch(`/api/hr/employees/${target._dbId}/profile`, { method: "PUT", body: JSON.stringify({ managerEmployeeCode: managerId || "" }) });
        } catch (e) {
          alert(`Couldn't update the manager: ${e.message}`);
          return;
        }
      }
    }
    setEmployeesState((es) => es.map((e) => (e.id === employeeId ? { ...e, manager: managerId || null } : e)));
    setProfileEmp((pe) => (pe && pe.id === employeeId ? { ...pe, manager: managerId || null } : pe));
  };
  const updateEmployeeRole = async (employeeId, newRole) => {
    if (API_BASE_URL) {
      const appUserId = appUserIdByEmployeeCode[employeeId];
      if (!appUserId) {
        alert("Couldn't find this account's server record — try logging out and back in as Master to refresh the list.");
        return;
      }
      try {
        await apiFetch(`/api/admin/users/${appUserId}/role`, { method: "PUT", body: JSON.stringify({ role: newRole }) });
      } catch (e) {
        alert(`Couldn't change the role: ${e.message}`);
        return;
      }
    }
    setEmployeesState((es) => es.map((e) => (e.id === employeeId ? { ...e, role: newRole } : e)));
  };

  if (screen === "login") return <LoginScreen onLogin={handleLogin} goSignup={() => setScreen("signup")} />;
  if (screen === "signup") return <SignupScreen onSignup={handleSignup} goLogin={() => setScreen("login")} />;

  const loginEmp = employeesState.find((e) => e.id === currentUserId);
  const role = loginEmp.role;

  const decideLeave = async (id, approve, signature, reason) => {
    if (API_BASE_URL) {
      const target = leaveRequests.find((r) => r.id === id);
      if (target && target._dbId) {
        const base = role === "manager" ? `/api/manager/team-leave/${target._dbId}` : `/api/hr/leave/${target._dbId}`;
        try {
          const updated = await apiFetch(`${base}/${approve ? "approve" : "reject"}`, { method: "PUT", body: JSON.stringify({ signature, reason: approve ? null : reason }) });
          const mapped = mapBackendLeaveRequest(updated);
          setLeaveRequests((rs) => rs.map((r) => (r.id === id ? mapped : r)));
          return;
        } catch (e) {
          alert(`Couldn't record this decision: ${e.message}`);
          return;
        }
      }
    }
    setLeaveRequests((rs) => rs.map((r) => (r.id === id ? {
      ...r,
      status: approve ? "Approved" : "Rejected",
      deciderId: loginEmp.id,
      deciderName: loginEmp.name,
      deciderSignature: signature,
      deciderSignedAt: new Date().toISOString(),
      decisionReason: approve ? null : reason,
    } : r)));
  };
  const addLeaveRequest = async (r) => {
    if (API_BASE_URL) {
      try {
        const created = await apiFetch("/api/me/leave", {
          method: "POST",
          body: JSON.stringify({ leaveType: r.type, startDate: r.start, endDate: r.end, reason: r.reason, signature: r.employeeSignature }),
        });
        const mapped = mapBackendLeaveRequest(created);
        setLeaveRequests((rs) => [mapped, ...rs]);
        return;
      } catch (e) {
        alert(`Couldn't submit your leave application: ${e.message}`);
        return;
      }
    }
    setLeaveRequests((rs) => [r, ...rs]);
  };
  const addSupportTicket = (t) => setSupportTickets((ts) => [t, ...ts]);
  const updateSupportTicket = (id, status, response) =>
    setSupportTickets((ts) => ts.map((t) => (t.id === id ? { ...t, status, response } : t)));
  const addOfficeIssue = (i) => setOfficeIssues((is) => [i, ...is]);
  const updateOfficeIssue = (id, status, response) =>
    setOfficeIssues((is) => is.map((i) => (i.id === id ? { ...i, status, response } : i)));

  const hrNav = [
    { id: "dashboard", label: "Dashboard", icon: LayoutDashboard }, { id: "employees", label: "Employees", icon: Users },
    { id: "payroll", label: "Payroll", icon: Banknote }, { id: "leave", label: "Leave", icon: CalendarDays },
    { id: "salaryStructure", label: "Salary Structure", icon: SlidersHorizontal },
  ];
  const adminNav = [
    { id: "overview", label: "Overview", icon: LayoutDashboard }, { id: "settings", label: "Company & Settings", icon: SlidersHorizontal },
    { id: "levels", label: "Levels & Departments", icon: Building2 }, { id: "users", label: "User Accounts", icon: ShieldCheck },
    { id: "support", label: "Support Tickets", icon: LifeBuoy }, { id: "officeIssues", label: "Office Issues", icon: MapPin },
  ];
  const itSupportNav = [
    { id: "officeIssues", label: "Office Issues", icon: MapPin }, { id: "support", label: "Support Tickets", icon: LifeBuoy },
    { id: "overview", label: "Overview", icon: LayoutDashboard },
    { id: "settings", label: "Company & Settings", icon: SlidersHorizontal },
    { id: "levels", label: "Levels & Departments", icon: Building2 }, { id: "users", label: "User Accounts", icon: ShieldCheck },
  ];
  const masterNav = [
    { id: "users", label: "User Accounts", icon: ShieldCheck }, { id: "employees", label: "Employees", icon: Users },
    { id: "payroll", label: "Payroll", icon: Banknote }, { id: "leave", label: "Leave", icon: CalendarDays },
    { id: "overview", label: "Overview", icon: LayoutDashboard },
    { id: "settings", label: "Company & Settings", icon: SlidersHorizontal },
    { id: "levels", label: "Levels & Departments", icon: Building2 },
    { id: "support", label: "Support Tickets", icon: LifeBuoy }, { id: "officeIssues", label: "Office Issues", icon: MapPin },
  ];
  const roleTitle = { master: "Master", admin: "Admin", hr: "HR", manager: "Manager", employee: "Employee", it_support: "IT Support" }[role];
  // True on the chooser itself, and anywhere inside whichever portal was
  // chosen (including sub-pages like Support/Office Issues reached from
  // the IT Support portal) — regardless of which viewMode got us there.
  const inEmployeeFlow = role === "employee" || viewMode === "selfService" || portalChoice !== null;

  const goRoleTab = (setter, id) => { setter(id); setViewMode("role"); };

  return (
    <div style={{ fontFamily: sans, background: T.bg, minHeight: 640, color: T.text, display: "flex", borderRadius: 10, overflow: "hidden", border: `1px solid ${T.border}` }}>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600;650;700&family=IBM+Plex+Mono:wght@400;500;600;700&display=swap');
        * { box-sizing: border-box; }
      `}</style>

      {/* SIDEBAR */}
      <div style={{ width: 220, background: T.navy, color: "#fff", padding: "20px 14px", flexShrink: 0, display: "flex", flexDirection: "column" }}>
        <div style={{ padding: "0 6px 16px" }}>
          <img src={COMPANY.logo} alt={COMPANY.name} style={{ height: 30, objectFit: "contain", display: "block" }} />
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: 9, background: "rgba(255,255,255,0.06)", borderRadius: 8, padding: "9px 10px", marginBottom: 16 }}>
          <div style={{ width: 30, height: 30, borderRadius: "50%", background: T.teal, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 12, fontWeight: 700, flexShrink: 0 }}>
            {loginEmp.name.split(" ").map((n) => n[0]).join("")}
          </div>
          <div style={{ minWidth: 0 }}>
            <div style={{ fontSize: 12.5, fontWeight: 650, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{loginEmp.name}</div>
            <RolePill role={role} />
          </div>
        </div>

        {role !== "employee" && (
          <button onClick={() => { setViewMode((v) => (v === "selfService" ? "role" : "selfService")); setPortalChoice(null); }} style={{
            display: "flex", alignItems: "center", gap: 7, background: viewMode === "selfService" ? T.teal : "rgba(255,255,255,0.08)",
            border: "none", color: "#fff", padding: "8px 10px", borderRadius: 6, fontSize: 12.5, fontWeight: 600, cursor: "pointer", marginBottom: 6, width: "100%",
          }}>
            <ArrowLeftRight size={14} />{viewMode === "selfService" ? `Back to ${roleTitle} view` : "Switch to my profile"}
          </button>
        )}

        {viewMode === "role" && role === "hr" && (
          <>
            <div style={{ fontSize: 10.5, color: "#8F8280", fontWeight: 700, letterSpacing: 0.4, padding: "10px 6px 8px" }}>HR</div>
            {hrNav.map((n) => (
              <button key={n.id} onClick={() => goRoleTab(setHrTab, n.id)} style={{ display: "flex", alignItems: "center", gap: 8, background: hrTab === n.id ? "rgba(255,255,255,0.08)" : "transparent", border: "none", color: hrTab === n.id ? "#fff" : "#C9BFBC", padding: "8px 10px", borderRadius: 6, fontSize: 13, fontWeight: 600, cursor: "pointer", textAlign: "left", marginBottom: 2 }}><n.icon size={15} /> {n.label}</button>
            ))}
          </>
        )}

        {viewMode === "role" && role === "admin" && (
          <>
            <div style={{ fontSize: 10.5, color: "#8F8280", fontWeight: 700, letterSpacing: 0.4, padding: "10px 6px 8px" }}>ADMIN</div>
            {adminNav.map((n) => (
              <button key={n.id} onClick={() => goRoleTab(setAdminTab, n.id)} style={{ display: "flex", alignItems: "center", gap: 8, background: adminTab === n.id ? "rgba(255,255,255,0.08)" : "transparent", border: "none", color: adminTab === n.id ? "#fff" : "#C9BFBC", padding: "8px 10px", borderRadius: 6, fontSize: 13, fontWeight: 600, cursor: "pointer", textAlign: "left", marginBottom: 2 }}><n.icon size={15} /> {n.label}</button>
            ))}
          </>
        )}

        {viewMode === "role" && role === "it_support" && (
          <>
            <div style={{ fontSize: 10.5, color: "#8F8280", fontWeight: 700, letterSpacing: 0.4, padding: "10px 6px 8px" }}>IT SUPPORT</div>
            {itSupportNav.map((n) => (
              <button key={n.id} onClick={() => goRoleTab(setItSupportTab, n.id)} style={{ display: "flex", alignItems: "center", gap: 8, background: itSupportTab === n.id ? "rgba(255,255,255,0.08)" : "transparent", border: "none", color: itSupportTab === n.id ? "#fff" : "#C9BFBC", padding: "8px 10px", borderRadius: 6, fontSize: 13, fontWeight: 600, cursor: "pointer", textAlign: "left", marginBottom: 2 }}><n.icon size={15} /> {n.label}</button>
            ))}
          </>
        )}

        {viewMode === "role" && role === "master" && (
          <>
            <div style={{ fontSize: 10.5, color: "#8F8280", fontWeight: 700, letterSpacing: 0.4, padding: "10px 6px 8px" }}>MASTER</div>
            {masterNav.map((n) => (
              <button key={n.id} onClick={() => goRoleTab(setMasterTab, n.id)} style={{ display: "flex", alignItems: "center", gap: 8, background: masterTab === n.id ? "rgba(255,255,255,0.08)" : "transparent", border: "none", color: masterTab === n.id ? "#fff" : "#C9BFBC", padding: "8px 10px", borderRadius: 6, fontSize: 13, fontWeight: 600, cursor: "pointer", textAlign: "left", marginBottom: 2 }}><n.icon size={15} /> {n.label}</button>
            ))}
          </>
        )}

        {viewMode === "role" && role === "manager" && <div style={{ fontSize: 12, color: "#C9BFBC", padding: "10px 6px" }}>Viewing your team's dashboard.</div>}

        <div style={{ marginTop: "auto", paddingTop: 14, borderTop: "1px solid rgba(255,255,255,0.1)" }}>
          {!inEmployeeFlow && (
            <>
              <button onClick={() => setViewMode("support")} style={{ display: "flex", alignItems: "center", gap: 8, color: viewMode === "support" ? "#fff" : "#C9BFBC", fontSize: 12.5, padding: "7px 6px", background: viewMode === "support" ? "rgba(255,255,255,0.08)" : "transparent", border: "none", borderRadius: 6, width: "100%", cursor: "pointer", fontWeight: 600 }}>
                <LifeBuoy size={14} /> Support
              </button>
              <button onClick={() => setViewMode("officeIssues")} style={{ display: "flex", alignItems: "center", gap: 8, color: viewMode === "officeIssues" ? "#fff" : "#C9BFBC", fontSize: 12.5, padding: "7px 6px", background: viewMode === "officeIssues" ? "rgba(255,255,255,0.08)" : "transparent", border: "none", borderRadius: 6, width: "100%", cursor: "pointer", fontWeight: 600 }}>
                <MapPin size={14} /> Office Issues
              </button>
            </>
          )}
          {inEmployeeFlow && portalChoice !== null && (
            <button onClick={() => { setPortalChoice(null); setViewMode(role === "employee" ? "role" : "selfService"); }} style={{ display: "flex", alignItems: "center", gap: 8, color: "#C9BFBC", fontSize: 12.5, padding: "7px 6px", background: "transparent", border: "none", borderRadius: 6, width: "100%", cursor: "pointer", fontWeight: 600 }}>
              <ArrowLeft size={14} /> Back to Portal Selection
            </button>
          )}
          <button onClick={() => setViewMode("settings")} style={{ display: "flex", alignItems: "center", gap: 8, color: viewMode === "settings" ? "#fff" : "#C9BFBC", fontSize: 12.5, padding: "7px 6px", background: viewMode === "settings" ? "rgba(255,255,255,0.08)" : "transparent", border: "none", borderRadius: 6, width: "100%", cursor: "pointer", fontWeight: 600 }}>
            <SettingsIcon size={14} /> Settings
          </button>
          <button onClick={handleLogout} style={{ display: "flex", alignItems: "center", gap: 8, color: "#C9BFBC", fontSize: 12.5, padding: "7px 6px", background: "transparent", border: "none", borderRadius: 6, width: "100%", cursor: "pointer", fontWeight: 600 }}>
            <LogOut size={14} /> Log out
          </button>
        </div>
      </div>

      {/* CONTENT */}
      <div style={{ flex: 1, padding: "26px 30px", overflowY: "auto", maxHeight: 720 }}>
        {viewMode === "settings" && <Settings emp={loginEmp} onSaveProfile={handleSaveProfile} onChangePassword={handleChangePassword} onBack={() => setViewMode("role")} />}
        {viewMode === "support" && <SupportCenter emp={loginEmp} tickets={supportTickets} onSubmit={addSupportTicket} onBack={() => setViewMode("role")} isAdminView={false} onUpdateTicket={updateSupportTicket} />}
        {viewMode === "officeIssues" && <OfficeIssueCenter emp={loginEmp} issues={officeIssues} onSubmit={addOfficeIssue} onBack={() => setViewMode("role")} isAdminView={false} availability={officeAvailability} onSetAvailability={setOfficeAvailability} onUpdateIssue={updateOfficeIssue} />}

        {viewMode === "selfService" && role !== "employee" && portalChoice === null && (
          <PortalChooser empName={loginEmp.name} onChoose={setPortalChoice} />
        )}
        {viewMode === "selfService" && role !== "employee" && portalChoice === "leave" && (
          <EmployeeView emp={loginEmp} leaveRequests={leaveRequests} addLeaveRequest={addLeaveRequest} history={history} setPayslipView={setPayslipView} onBackToChooser={() => setPortalChoice(null)} />
        )}
        {viewMode === "selfService" && role !== "employee" && portalChoice === "itSupport" && (
          <ITSupportPortal goSupport={() => setViewMode("support")} goOfficeIssues={() => setViewMode("officeIssues")} />
        )}

        {viewMode === "role" && role === "hr" && hrTab === "dashboard" && <HrDashboard leaveRequests={leaveRequests} payrollStage={payrollStage} advanceStage={advanceStage} />}
        {viewMode === "role" && role === "hr" && hrTab === "employees" && <HrEmployees onOpenProfile={setProfileEmp} onUpdateSalary={updateEmployeeSalary} />}
        {viewMode === "role" && role === "hr" && hrTab === "payroll" && <HrPayroll payrollStage={payrollStage} setPayslipView={setPayslipView} payrollRecords={payrollRecords} resendPayslipEmail={resendPayslipEmail} payrollLoading={payrollLoading} advanceStage={advanceStage} />}
        {viewMode === "role" && role === "hr" && hrTab === "leave" && <HrLeave leaveRequests={leaveRequests} decider={loginEmp} onDecide={decideLeave} />}
        {viewMode === "role" && role === "hr" && hrTab === "salaryStructure" && <AdminLevels onUpdateLevel={updateLevel} onAddLevel={addLevel} />}

        {viewMode === "role" && role === "admin" && adminTab === "overview" && <AdminOverview supportTickets={supportTickets} officeIssues={officeIssues} />}
        {viewMode === "role" && role === "admin" && adminTab === "settings" && <AdminCompanySettings />}
        {viewMode === "role" && role === "admin" && adminTab === "levels" && <AdminLevels onUpdateLevel={updateLevel} onAddLevel={addLevel} />}
        {viewMode === "role" && role === "admin" && adminTab === "users" && <AdminUsers currentUserId={currentUserId} isMaster={false} />}
        {viewMode === "role" && role === "admin" && adminTab === "support" && <SupportCenter emp={loginEmp} tickets={supportTickets} onSubmit={addSupportTicket} isAdminView={true} onUpdateTicket={updateSupportTicket} />}
        {viewMode === "role" && role === "admin" && adminTab === "officeIssues" && <OfficeIssueCenter emp={loginEmp} issues={officeIssues} onSubmit={addOfficeIssue} isAdminView={true} availability={officeAvailability} onSetAvailability={setOfficeAvailability} onUpdateIssue={updateOfficeIssue} />}

        {viewMode === "role" && role === "it_support" && itSupportTab === "officeIssues" && <OfficeIssueCenter emp={loginEmp} issues={officeIssues} onSubmit={addOfficeIssue} isAdminView={true} availability={officeAvailability} onSetAvailability={setOfficeAvailability} onUpdateIssue={updateOfficeIssue} />}
        {viewMode === "role" && role === "it_support" && itSupportTab === "support" && <SupportCenter emp={loginEmp} tickets={supportTickets} onSubmit={addSupportTicket} isAdminView={true} onUpdateTicket={updateSupportTicket} />}
        {viewMode === "role" && role === "it_support" && itSupportTab === "overview" && <AdminOverview supportTickets={supportTickets} officeIssues={officeIssues} />}
        {viewMode === "role" && role === "it_support" && itSupportTab === "settings" && <AdminCompanySettings />}
        {viewMode === "role" && role === "it_support" && itSupportTab === "levels" && <AdminLevels onUpdateLevel={updateLevel} onAddLevel={addLevel} />}
        {viewMode === "role" && role === "it_support" && itSupportTab === "users" && <AdminUsers currentUserId={currentUserId} isMaster={false} />}

        {viewMode === "role" && role === "master" && masterTab === "users" && <AdminUsers currentUserId={currentUserId} isMaster={true} onChangeRole={updateEmployeeRole} onRefresh={refreshAppUsers} />}
        {viewMode === "role" && role === "master" && masterTab === "employees" && <HrEmployees onOpenProfile={setProfileEmp} onUpdateSalary={updateEmployeeSalary} />}
        {viewMode === "role" && role === "master" && masterTab === "payroll" && <HrPayroll payrollStage={payrollStage} setPayslipView={setPayslipView} payrollRecords={payrollRecords} resendPayslipEmail={resendPayslipEmail} payrollLoading={payrollLoading} advanceStage={advanceStage} />}
        {viewMode === "role" && role === "master" && masterTab === "leave" && <HrLeave leaveRequests={leaveRequests} decider={loginEmp} onDecide={decideLeave} />}
        {viewMode === "role" && role === "master" && masterTab === "overview" && <AdminOverview supportTickets={supportTickets} officeIssues={officeIssues} />}
        {viewMode === "role" && role === "master" && masterTab === "settings" && <AdminCompanySettings />}
        {viewMode === "role" && role === "master" && masterTab === "levels" && <AdminLevels onUpdateLevel={updateLevel} onAddLevel={addLevel} />}
        {viewMode === "role" && role === "master" && masterTab === "support" && <SupportCenter emp={loginEmp} tickets={supportTickets} onSubmit={addSupportTicket} isAdminView={true} onUpdateTicket={updateSupportTicket} />}
        {viewMode === "role" && role === "master" && masterTab === "officeIssues" && <OfficeIssueCenter emp={loginEmp} issues={officeIssues} onSubmit={addOfficeIssue} isAdminView={true} availability={officeAvailability} onSetAvailability={setOfficeAvailability} onUpdateIssue={updateOfficeIssue} />}

        {viewMode === "role" && role === "manager" && <ManagerView manager={loginEmp} leaveRequests={leaveRequests} onDecide={decideLeave} allEmployees={employeesState} />}

        {viewMode === "role" && role === "employee" && portalChoice === null && (
          <PortalChooser empName={loginEmp.name} onChoose={setPortalChoice} />
        )}
        {viewMode === "role" && role === "employee" && portalChoice === "leave" && (
          <EmployeeView emp={loginEmp} leaveRequests={leaveRequests} addLeaveRequest={addLeaveRequest} history={history} setPayslipView={setPayslipView} onBackToChooser={() => setPortalChoice(null)} />
        )}
        {viewMode === "role" && role === "employee" && portalChoice === "itSupport" && (
          <ITSupportPortal goSupport={() => setViewMode("support")} goOfficeIssues={() => setViewMode("officeIssues")} />
        )}
      </div>

      {profileEmp && <ProfileDrawer emp={profileEmp} onClose={() => setProfileEmp(null)} onUpdateManager={updateEmployeeManager} />}
      {payslipView && <Payslip {...payslipView} onClose={() => setPayslipView(null)} />}
    </div>
  );
}
