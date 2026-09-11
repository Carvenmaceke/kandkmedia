import React, { useState, useMemo, useRef, useEffect } from "react";
import jsPDF from "jspdf";
import {
  Users, Banknote, CalendarDays, FileText, Bell, CheckCircle2, XCircle,
  Clock, ChevronRight, Building2, Search, Download, Eye, X, Send,
  UserCircle2, LayoutDashboard, ClipboardList, Settings as SettingsIcon, LogOut,
  ArrowRight, ArrowLeft, ShieldCheck, SlidersHorizontal, KeyRound, ArrowLeftRight,
  Lock, Mail, Phone as PhoneIcon, AlertCircle, PenLine, Trash2, Paperclip, FileCheck2, LifeBuoy, Copy,
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

const LEVELS = [
  { name: "Intern", default: 4000, min: 3500, max: 6000 },
  { name: "Junior", default: 12000, min: 8000, max: 15000 },
  { name: "Mid-Level", default: 20000, min: 15000, max: 25000 },
  { name: "Senior", default: 30000, min: 25000, max: 45000 },
  { name: "Manager", default: 42000, min: 38000, max: 55000 },
];

const DEPARTMENTS = ["Digital Media", "Creative Services", "Publications", "Events Management", "Sales", "HR", "Admin"];

const SUPPORT_EMAIL = "itsupport@kandkmedia.co.za";
const SUPPORT_CATEGORIES = ["System Malfunction / Bug", "Payroll Question", "Leave Question", "Account / Access Issue", "Other"];
const SUPPORT_PRIORITIES = ["Low", "Medium", "High", "Urgent"];

const LEAVE_TYPES = [
  "Annual Leave", "Sick Leave", "Family Responsibility Leave",
  "Study Leave", "Unpaid Leave", "Maternity Leave", "Parental Leave",
];

// Leave types that require supporting proof (a medical certificate, exam
// timetable, etc.) before HR/a manager can responsibly decide the request.
const PROOF_REQUIRED_TYPES = ["Sick Leave", "Maternity Leave", "Parental Leave", "Family Responsibility Leave", "Study Leave"];
const MAX_PROOF_FILE_BYTES = 4 * 1024 * 1024; // 4MB

// role: "admin" | "hr" | "manager" | "employee"
// `EMPLOYEES` and `LEAVE_BALANCES` are `let`, not `const` — the App component
// syncs them from React state each render, so every screen always reads the
// latest signed-up users / edited profiles without a big prop-drilling pass.
let EMPLOYEES = [
  { id: "EMP-00009", name: "Karabo Mahlangu", role: "admin", level: "Manager", position: "System Administrator", dept: "Admin", salary: 46000, manager: null, start: "2016-04-18", email: "karabo.mahlangu@kandkmedia.co.za", phone: "082 111 2233" },
  { id: "EMP-00010", name: "Lindiwe Zulu", role: "hr", level: "Manager", position: "HR Manager", dept: "HR", salary: 41000, manager: null, start: "2015-10-02", email: "lindiwe.zulu@kandkmedia.co.za", phone: "082 222 3344" },
  { id: "EMP-00005", name: "Thabo Nkosi", role: "manager", level: "Manager", position: "Digital Media Manager", dept: "Digital Media", salary: 45000, manager: null, email: "thabo.nkosi@kandkmedia.co.za", start: "2018-05-11", phone: "082 333 4455" },
  { id: "EMP-00007", name: "Grace Sithole", role: "manager", level: "Manager", position: "Creative & Events Manager", dept: "Creative Services", salary: 43000, manager: null, start: "2017-09-04", email: "grace.sithole@kandkmedia.co.za", phone: "082 444 5566" },
  { id: "EMP-00001", name: "John Doe", role: "employee", level: "Junior", position: "Web Developer", dept: "Digital Media", salary: 13500, manager: "EMP-00005", start: "2023-03-01", email: "john.doe@kandkmedia.co.za", phone: "082 555 6677" },
  { id: "EMP-00002", name: "Amahle Dlamini", role: "employee", level: "Mid-Level", position: "Social Media Manager", dept: "Digital Media", salary: 21000, manager: "EMP-00005", start: "2022-07-14", email: "amahle.dlamini@kandkmedia.co.za", phone: "082 666 7788" },
  { id: "EMP-00003", name: "Pieter van der Merwe", role: "employee", level: "Senior", position: "Senior Videographer", dept: "Creative Services", salary: 34000, manager: "EMP-00007", start: "2019-01-20", email: "pieter.vdmerwe@kandkmedia.co.za", phone: "082 777 8899" },
  { id: "EMP-00004", name: "Naledi Mokoena", role: "employee", level: "Intern", position: "Digital Media Intern", dept: "Digital Media", salary: 4500, manager: "EMP-00005", start: "2026-02-03", email: "naledi.mokoena@kandkmedia.co.za", phone: "082 888 9900" },
  { id: "EMP-00006", name: "Sarah Botha", role: "employee", level: "Junior", position: "Copywriter", dept: "Publications", salary: 12500, manager: "EMP-00007", start: "2024-01-09", email: "sarah.botha@kandkmedia.co.za", phone: "082 999 0011" },
  { id: "EMP-00008", name: "Michael Chen", role: "employee", level: "Mid-Level", position: "Sales Executive", dept: "Sales", salary: 19500, manager: "EMP-00007", start: "2021-11-22", email: "michael.chen@kandkmedia.co.za", phone: "083 111 2200" },
  { id: "EMP-00011", name: "Support Desk", role: "employee", level: "Junior", position: "Support Coordinator", dept: "Admin", salary: 12000, manager: null, start: "2023-01-01", email: "support@kandkmedia.co.za", phone: "083 222 3300" },
];

const ROLE_LABEL = { admin: "Admin", hr: "HR", manager: "Manager", employee: "Employee" };
const ROLE_TONE = { admin: "purple", hr: "teal", manager: "amber", employee: "muted" };

let LEAVE_BALANCES = {
  "EMP-00001": { "Annual Leave": 12, "Sick Leave": 8, "Family Responsibility Leave": 3 },
  "EMP-00002": { "Annual Leave": 9, "Sick Leave": 10, "Family Responsibility Leave": 3 },
  "EMP-00003": { "Annual Leave": 15, "Sick Leave": 9, "Family Responsibility Leave": 2 },
  "EMP-00004": { "Annual Leave": 5, "Sick Leave": 6, "Family Responsibility Leave": 3 },
  "EMP-00005": { "Annual Leave": 18, "Sick Leave": 10, "Family Responsibility Leave": 3 },
  "EMP-00006": { "Annual Leave": 11, "Sick Leave": 7, "Family Responsibility Leave": 1 },
  "EMP-00007": { "Annual Leave": 16, "Sick Leave": 10, "Family Responsibility Leave": 3 },
  "EMP-00008": { "Annual Leave": 8, "Sick Leave": 5, "Family Responsibility Leave": 2 },
  "EMP-00011": { "Annual Leave": 15, "Sick Leave": 10, "Family Responsibility Leave": 3 },
  "EMP-00009": { "Annual Leave": 20, "Sick Leave": 10, "Family Responsibility Leave": 3 },
  "EMP-00010": { "Annual Leave": 17, "Sick Leave": 10, "Family Responsibility Leave": 3 },
};

const INITIAL_LEAVE_REQUESTS = [
  { id: "LR-101", emp: "EMP-00001", type: "Annual Leave", start: "2026-09-15", end: "2026-09-19", days: 5, reason: "Family commitment", status: "Pending" },
  { id: "LR-102", emp: "EMP-00006", type: "Sick Leave", start: "2026-09-08", end: "2026-09-08", days: 1, reason: "Flu", status: "Approved" },
  { id: "LR-103", emp: "EMP-00008", type: "Annual Leave", start: "2026-09-22", end: "2026-09-24", days: 3, reason: "Personal travel", status: "Pending" },
  { id: "LR-104", emp: "EMP-00004", type: "Study Leave", start: "2026-09-11", end: "2026-09-11", days: 1, reason: "Exam", status: "Rejected" },
  { id: "LR-105", emp: "EMP-00002", type: "Family Responsibility Leave", start: "2026-09-29", end: "2026-09-30", days: 2, reason: "Child's school event", status: "Pending" },
];

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
  }[tone];
  return <span style={{ background: map.bg, color: map.fg, fontSize: 12, fontWeight: 600, padding: "3px 9px", borderRadius: 4, whiteSpace: "nowrap" }}>{children}</span>;
}
function RolePill({ role }) { return <Pill tone={ROLE_TONE[role]}>{ROLE_LABEL[role]}</Pill>; }
function StatusPill({ status }) {
  const tone = status === "Approved" ? "green" : status === "Rejected" ? "red" : "amber";
  return <Pill tone={tone}>{status}</Pill>;
}
function Card({ children, style }) {
  return <div style={{ background: T.surface, border: `1px solid ${T.border}`, borderRadius: 8, ...style }}>{children}</div>;
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
function StatCard({ icon: Icon, label, value, tone }) {
  const c = tone || T.navy;
  return (
    <Card style={{ padding: "16px 18px", flex: 1, minWidth: 150 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 10 }}>
        <div style={{ width: 30, height: 30, borderRadius: 6, background: T.tealLight, display: "flex", alignItems: "center", justifyContent: "center" }}><Icon size={16} color={T.teal} /></div>
        <span style={{ fontSize: 12.5, color: T.muted, fontWeight: 600 }}>{label}</span>
      </div>
      <div style={{ fontFamily: mono, fontSize: 26, fontWeight: 600, color: c }}>{value}</div>
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


function ProfileDrawer({ emp, onClose }) {
  if (!emp) return null;
  const mgr = emp.manager ? empById(emp.manager) : null;
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(20,10,9,0.45)", zIndex: 40, display: "flex", justifyContent: "flex-end" }} onClick={onClose}>
      <div style={{ width: 340, background: T.surface, height: "100%", padding: 24, boxSizing: "border-box" }} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
          <div style={{ width: 46, height: 46, borderRadius: "50%", background: T.navy, color: "#fff", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 700, fontSize: 16 }}>{emp.name.split(" ").map((n) => n[0]).join("")}</div>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} /></button>
        </div>
        <div style={{ marginTop: 14, fontSize: 17, fontWeight: 700 }}>{emp.name}</div>
        <div style={{ fontSize: 12.5, color: T.muted, fontFamily: mono }}>{emp.id}</div>
        <div style={{ marginTop: 6, display: "flex", gap: 6 }}><Pill tone="teal">{emp.level}</Pill><RolePill role={emp.role} /></div>
        <div style={{ marginTop: 20, display: "flex", flexDirection: "column", gap: 12, fontSize: 13 }}>
          {[["Position", emp.position], ["Department", emp.dept], ["Email", emp.email], ["Phone", emp.phone || "—"], ["Start Date", emp.start], ["Reports To", mgr ? mgr.name : "—"], ["Salary", money(emp.salary)]].map(([k, v]) => (
            <div key={k}>
              <div style={{ fontSize: 11, color: T.muted, fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.3 }}>{k}</div>
              <div style={{ marginTop: 2 }}>{v}</div>
            </div>
          ))}
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
      fontFamily: sans, minHeight: 640, borderRadius: 10, overflow: "hidden", border: `1px solid ${T.border}`,
      background: `radial-gradient(circle at 20% 20%, #3A1315, ${T.navyDeep} 62%)`,
      display: "flex", alignItems: "center", justifyContent: "center", padding: "40px 20px",
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

  const submit = (e) => {
    e.preventDefault();
    const err = onLogin(email.trim(), password);
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
        <Button type="submit" variant="teal" full>Log In</Button>
      </form>
      <div style={{ marginTop: 16, fontSize: 12.5, color: T.muted, textAlign: "center" }}>
        Don't have an account? <button onClick={goSignup} style={{ background: "none", border: "none", color: T.teal, fontWeight: 700, cursor: "pointer", fontSize: 12.5, padding: 0 }}>Sign up</button>
      </div>
      <div style={{ marginTop: 18, paddingTop: 14, borderTop: `1px solid ${T.border}`, fontSize: 11, color: T.muted, lineHeight: 1.8 }}>
        Demo accounts (password: <span style={{ fontFamily: mono }}>{DEFAULT_PASSWORD}</span>):<br />
        HR — lindiwe.zulu@kandkmedia.co.za<br />
        Admin — karabo.mahlangu@kandkmedia.co.za<br />
        Manager — thabo.nkosi@kandkmedia.co.za<br />
        Employee — john.doe@kandkmedia.co.za
      </div>
    </AuthShell>
  );
}

function SignupScreen({ onSignup, goLogin }) {
  const [form, setForm] = useState({
    name: "", email: "", phone: "", password: "", confirm: "",
    role: "employee", dept: DEPARTMENTS[0], position: "", level: "Junior",
  });
  const [error, setError] = useState("");
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = (e) => {
    e.preventDefault();
    if (!form.name || !form.email || !form.password) { setError("Please fill in all required fields."); return; }
    if (!isCompanyEmail(form.email)) { setError(`Please use your company email address, ending in @${ALLOWED_EMAIL_DOMAIN}.`); return; }
    if (form.password !== form.confirm) { setError("Passwords do not match."); return; }
    if (EMPLOYEES.some((emp) => emp.email.toLowerCase() === form.email.toLowerCase())) { setError("An account with that email already exists."); return; }
    setError("");
    onSignup(form);
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

        <Field label="I am signing up as">
          <div style={{ display: "flex", gap: 8 }}>
            {["employee", "hr", "admin"].map((r) => (
              <button type="button" key={r} onClick={() => setForm({ ...form, role: r })} style={{
                flex: 1, padding: "9px 6px", borderRadius: 6, cursor: "pointer", fontSize: 12.5, fontWeight: 700,
                border: `1px solid ${form.role === r ? T.teal : T.border}`,
                background: form.role === r ? T.tealLight : "#fff", color: form.role === r ? T.teal : T.muted,
              }}>{ROLE_LABEL[r]}</button>
            ))}
          </div>
          <div style={{ fontSize: 11, color: T.muted, marginTop: 6 }}>
            {form.role === "employee" && "You'll see your own profile, payslips and leave — nothing else."}
            {form.role === "hr" && "You'll manage employees, payroll and leave for the whole company."}
            {form.role === "admin" && "You'll manage company settings, employee levels/departments and user accounts."}
          </div>
        </Field>

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

        {form.role === "employee" && (
          <Field label="Employee Level">
            <select value={form.level} onChange={set("level")} style={inputStyle}>
              {LEVELS.map((l) => <option key={l.name}>{l.name}</option>)}
            </select>
            <div style={{ fontSize: 11, color: T.muted, marginTop: 4 }}>
              Starting salary (default for this level): {money(LEVELS.find((l) => l.name === form.level).default)} — HR can adjust this later.
            </div>
          </Field>
        )}

        {error && (
          <div style={{ display: "flex", gap: 6, alignItems: "center", color: T.red, background: T.redBg, padding: "8px 10px", borderRadius: 6, fontSize: 12.5, marginBottom: 14 }}>
            <AlertCircle size={14} /> {error}
          </div>
        )}
        <Button type="submit" variant="teal" full>Create Account</Button>
      </form>
      <div style={{ marginTop: 16, fontSize: 12.5, color: T.muted, textAlign: "center" }}>
        Already have an account? <button onClick={goLogin} style={{ background: "none", border: "none", color: T.teal, fontWeight: 700, cursor: "pointer", fontSize: 12.5, padding: 0 }}>Log in</button>
      </div>
    </AuthShell>
  );
}

/* ---------------------------------------------------------------------- */
/* SETTINGS — edit my profile                                             */
/* ---------------------------------------------------------------------- */
/* ---------------------------------------------------------------------- */
/* SUPPORT CENTER — request help, routed to IT support                    */
/* ---------------------------------------------------------------------- */
function buildSupportMailto(ticket, emp) {
  const subject = `[${ticket.priority}] ${ticket.category}: ${ticket.subject}`;
  const body =
    `Employee: ${emp.name} (${emp.id})\n` +
    `Role: ${ROLE_LABEL[emp.role]}\n` +
    `Department: ${emp.dept}\n` +
    `Category: ${ticket.category}\n` +
    `Priority: ${ticket.priority}\n\n` +
    `${ticket.description}\n`;
  return `mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
}

function SupportCenter({ emp, tickets, onSubmit, onBack, isAdminView }) {
  const [view, setView] = useState(isAdminView ? "all" : "new");
  const [form, setForm] = useState({ subject: "", category: SUPPORT_CATEGORIES[0], priority: "Medium", description: "" });
  const [error, setError] = useState("");
  const [justSubmitted, setJustSubmitted] = useState(null);

  const myTickets = tickets.filter((t) => t.empId === emp.id);

  const submit = () => {
    if (!form.subject.trim() || !form.description.trim()) { setError("Please fill in a subject and description."); return; }
    setError("");
    const ticket = {
      id: `TCK-${Math.floor(Math.random() * 9000 + 1000)}`,
      empId: emp.id, empName: emp.name, subject: form.subject.trim(), category: form.category,
      priority: form.priority, description: form.description.trim(), status: "Open",
      createdAt: new Date().toISOString(),
    };
    onSubmit(ticket);
    window.location.href = buildSupportMailto(ticket, emp);
    setJustSubmitted(ticket);
    setForm({ subject: "", category: SUPPORT_CATEGORIES[0], priority: "Medium", description: "" });
  };

  const tabs = isAdminView
    ? [{ id: "all", label: "All Support Tickets" }]
    : [{ id: "new", label: "New Request" }, { id: "mine", label: "My Requests" }];

  return (
    <div>
      {onBack && (
        <button onClick={onBack} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "none", color: T.muted, fontSize: 13, fontWeight: 600, cursor: "pointer", padding: 0, marginBottom: 14 }}>
          <ArrowLeft size={15} /> Back
        </button>
      )}
      <SectionTitle sub={`Requests are emailed directly to ${SUPPORT_EMAIL}`}>Help &amp; Support</SectionTitle>

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
                <CheckCircle2 size={18} /> <span style={{ fontWeight: 700, fontSize: 14 }}>Request logged</span>
              </div>
              <div style={{ fontSize: 13, color: T.muted, lineHeight: 1.7, marginBottom: 16 }}>
                Your email app should have opened with the details pre-filled, addressed to <strong>{SUPPORT_EMAIL}</strong> — hit send from there to actually deliver it. If nothing opened (some browsers block this), use the buttons below instead.
              </div>
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                <Button variant="teal" small icon={Mail} onClick={() => { window.location.href = buildSupportMailto(justSubmitted, emp); }}>Open Email Again</Button>
                <Button variant="ghost" small icon={Copy} onClick={() => { navigator.clipboard?.writeText(`To: ${SUPPORT_EMAIL}\nSubject: [${justSubmitted.priority}] ${justSubmitted.category}: ${justSubmitted.subject}\n\n${justSubmitted.description}`); }}>Copy Details</Button>
                <Button variant="ghost" small onClick={() => setJustSubmitted(null)}>Submit Another</Button>
              </div>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              {form.category === "System Malfunction / Bug" && (
                <div style={{ fontSize: 11.5, color: T.muted, background: T.bg, padding: "8px 10px", borderRadius: 6 }}>
                  Reporting a bug or outage? Set priority to <strong>Urgent</strong> if it's stopping you from working.
                </div>
              )}
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
              <Button variant="teal" icon={Mail} onClick={submit}>Send to IT Support</Button>
            </div>
          )}
        </Card>
      )}

      {view === "mine" && (
        <Card style={{ overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead><tr style={{ background: T.bg, textAlign: "left" }}>{["Subject", "Category", "Priority", "Status", "Submitted"].map((h) => <th key={h} style={{ padding: "10px 14px", fontSize: 11.5, color: T.muted, fontWeight: 700 }}>{h}</th>)}</tr></thead>
            <tbody>
              {myTickets.length === 0 && <tr><td colSpan={5} style={{ padding: 18, textAlign: "center", color: T.muted }}>No support requests yet.</td></tr>}
              {myTickets.map((t) => (
                <tr key={t.id} style={{ borderTop: `1px solid ${T.border}` }}>
                  <td style={{ padding: "10px 14px", fontWeight: 600 }}>{t.subject}</td>
                  <td style={{ padding: "10px 14px", color: T.muted }}>{t.category}</td>
                  <td style={{ padding: "10px 14px" }}><Pill tone={t.priority === "Urgent" ? "red" : t.priority === "High" ? "amber" : "muted"}>{t.priority}</Pill></td>
                  <td style={{ padding: "10px 14px" }}><Pill tone={t.status === "Resolved" ? "green" : "teal"}>{t.status}</Pill></td>
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
            <thead><tr style={{ background: T.bg, textAlign: "left" }}>{["Employee", "Subject", "Category", "Priority", "Status", "Submitted"].map((h) => <th key={h} style={{ padding: "10px 14px", fontSize: 11.5, color: T.muted, fontWeight: 700 }}>{h}</th>)}</tr></thead>
            <tbody>
              {tickets.length === 0 && <tr><td colSpan={6} style={{ padding: 18, textAlign: "center", color: T.muted }}>No support requests yet.</td></tr>}
              {[...tickets].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)).map((t) => (
                <tr key={t.id} style={{ borderTop: `1px solid ${T.border}` }}>
                  <td style={{ padding: "10px 14px" }}>{t.empName}</td>
                  <td style={{ padding: "10px 14px", fontWeight: 600 }}>{t.subject}</td>
                  <td style={{ padding: "10px 14px", color: T.muted }}>{t.category}</td>
                  <td style={{ padding: "10px 14px" }}><Pill tone={t.priority === "Urgent" ? "red" : t.priority === "High" ? "amber" : "muted"}>{t.priority}</Pill></td>
                  <td style={{ padding: "10px 14px" }}><Pill tone={t.status === "Resolved" ? "green" : "teal"}>{t.status}</Pill></td>
                  <td style={{ padding: "10px 14px", fontFamily: mono, fontSize: 12 }}>{new Date(t.createdAt).toLocaleDateString("en-ZA")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </div>
  );
}


function Settings({ emp, onSaveProfile, onChangePassword, onBack }) {
  const [form, setForm] = useState({ name: emp.name, email: emp.email, phone: emp.phone || "", position: emp.position, dept: emp.dept });
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

function HrEmployees({ onOpenProfile }) {
  const [q, setQ] = useState("");
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
                <td style={{ padding: "10px 14px" }}><Pill tone="teal">{e.level}</Pill></td>
                <td style={{ padding: "10px 14px", color: T.muted }}>{e.position}</td>
                <td style={{ padding: "10px 14px", color: T.muted }}>{e.dept}</td>
                <td style={{ padding: "10px 14px", fontFamily: mono }}>{money(e.salary)}</td>
                <td style={{ padding: "10px 14px" }}><button onClick={() => onOpenProfile(e)} style={{ background: "none", border: "none", cursor: "pointer", color: T.teal }}><ChevronRight size={16} /></button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </div>
  );
}

function HrPayroll({ payrollStage, setPayslipView }) {
  return (
    <div>
      <SectionTitle sub={`Reviewing variable earnings and deductions for ${CURRENT_MONTH}`}>Payroll — {CURRENT_MONTH}</SectionTitle>
      <div style={{ marginBottom: 14 }}><Pill tone="teal">Status: {payrollStage}</Pill></div>
      <Card style={{ overflow: "hidden" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead><tr style={{ background: T.bg, textAlign: "left" }}>{["Employee", "Basic", "Overtime", "Bonus", "Gross", "Deductions", "Net Pay", ""].map((h) => <th key={h} style={{ padding: "10px 14px", fontSize: 11.5, color: T.muted, fontWeight: 700 }}>{h}</th>)}</tr></thead>
          <tbody>
            {EMPLOYEES.map((e) => {
              const f = calcPayroll(e, 3);
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
                    <div style={{ display: "flex", gap: 10 }}>
                      <button onClick={() => setPayslipView({ emp: e, month: CURRENT_MONTH, figures: f })} style={{ background: "none", border: "none", cursor: "pointer", color: T.teal }} title="Preview payslip"><Eye size={16} /></button>
                      <button onClick={() => downloadPayslipPdf(e, CURRENT_MONTH, f)} style={{ background: "none", border: "none", cursor: "pointer", color: T.muted }} title="Download PDF"><Download size={16} /></button>
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
function AdminOverview({ supportTickets }) {
  const roleCounts = ["admin", "hr", "manager", "employee"].map((r) => ({ role: r, count: EMPLOYEES.filter((e) => e.role === r).length }));
  const openTickets = supportTickets.filter((t) => t.status !== "Resolved").length;
  return (
    <div>
      <SectionTitle sub="System-level status for the whole platform">System Overview</SectionTitle>
      <div style={{ display: "flex", gap: 14, flexWrap: "wrap", marginBottom: 22 }}>
        <StatCard icon={Users} label="User Accounts" value={EMPLOYEES.length} />
        <StatCard icon={Building2} label="Departments" value={DEPARTMENTS.length} />
        <StatCard icon={SlidersHorizontal} label="Employee Levels" value={LEVELS.length} />
        <StatCard icon={LifeBuoy} label="Open Support Tickets" value={openTickets} tone={openTickets > 0 ? T.amber : T.green} />
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

function AdminLevels() {
  return (
    <div>
      <SectionTitle sub="Configure default salary structures and organizational departments">Employee Levels & Departments</SectionTitle>
      <div style={{ display: "flex", gap: 20, flexWrap: "wrap" }}>
        <Card style={{ overflow: "hidden", flex: "2 1 420px" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead><tr style={{ background: T.bg, textAlign: "left" }}>{["Level", "Default Salary", "Range"].map((h) => <th key={h} style={{ padding: "10px 14px", fontSize: 11.5, color: T.muted, fontWeight: 700 }}>{h}</th>)}</tr></thead>
            <tbody>
              {LEVELS.map((l) => (
                <tr key={l.name} style={{ borderTop: `1px solid ${T.border}` }}>
                  <td style={{ padding: "10px 14px", fontWeight: 600 }}>{l.name}</td>
                  <td style={{ padding: "10px 14px", fontFamily: mono }}>{money(l.default)}</td>
                  <td style={{ padding: "10px 14px", fontFamily: mono, color: T.muted }}>{money(l.min)} – {money(l.max)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
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

function AdminUsers({ currentUserId }) {
  return (
    <div>
      <SectionTitle sub="Every account and its assigned system role">User Accounts</SectionTitle>
      <Card style={{ overflow: "hidden" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead><tr style={{ background: T.bg, textAlign: "left" }}>{["Employee ID", "Name", "Role", "Email", "Status", ""].map((h) => <th key={h} style={{ padding: "10px 14px", fontSize: 11.5, color: T.muted, fontWeight: 700 }}>{h}</th>)}</tr></thead>
          <tbody>
            {EMPLOYEES.map((e) => (
              <tr key={e.id} style={{ borderTop: `1px solid ${T.border}`, background: e.id === currentUserId ? T.tealLight : "transparent" }}>
                <td style={{ padding: "10px 14px", fontFamily: mono, fontSize: 12 }}>{e.id}</td>
                <td style={{ padding: "10px 14px", fontWeight: 600 }}>{e.name}{e.id === currentUserId && <span style={{ color: T.muted, fontWeight: 400 }}> (you)</span>}</td>
                <td style={{ padding: "10px 14px" }}><RolePill role={e.role} /></td>
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
                <td style={{ padding: "10px 14px" }}><Pill tone="teal">{e.level}</Pill></td>
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
function EmployeeView({ emp, leaveRequests, addLeaveRequest, history, setPayslipView }) {
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
            {Object.entries(balances).map(([k, v]) => <StatCard key={k} icon={CalendarDays} label={k} value={`${v}d`} />)}
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
  const [employeesState, setEmployeesState] = useState(EMPLOYEES);
  const [balancesState, setBalancesState] = useState(LEAVE_BALANCES);
  const [viewMode, setViewMode] = useState("role"); // "role" | "selfService" | "settings"
  const [hrTab, setHrTab] = useState("dashboard");
  const [adminTab, setAdminTab] = useState("overview");
  const [leaveRequests, setLeaveRequests] = useState(INITIAL_LEAVE_REQUESTS);
  const [supportTickets, setSupportTickets] = useState([]);
  const [payrollStage, setPayrollStage] = useState("DRAFT");
  const [profileEmp, setProfileEmp] = useState(null);
  const [payslipView, setPayslipView] = useState(null);

  // Sync the module-level mutable data with React state every render, so
  // every component below always reads the latest signups / profile edits.
  EMPLOYEES = employeesState;
  LEAVE_BALANCES = balancesState;

  const history = useMemo(() => buildHistory(employeesState.filter((e) => PAST_MONTHS && e.start <= "2026-06-01")), [employeesState]);

  const handleLogin = (email, password) => {
    const user = employeesState.find((e) => e.email.toLowerCase() === email.toLowerCase());
    if (!user) return "No account found with that email address.";
    const expected = user.password || DEFAULT_PASSWORD;
    if (password !== expected) return "Incorrect password.";
    setCurrentUserId(user.id);
    setViewMode("role");
    setScreen("app");
    return null;
  };

  const handleSignup = (form) => {
    const id = nextEmployeeId();
    const level = form.role === "employee" ? form.level : "Manager";
    const salary = LEVELS.find((l) => l.name === level)?.default || 12000;
    const newEmp = {
      id, name: form.name, role: form.role, level, dept: form.dept,
      position: form.position || (form.role === "hr" ? "HR Officer" : form.role === "admin" ? "System Administrator" : "Employee"),
      salary, manager: null, start: "2026-09-10", email: form.email, phone: form.phone, password: form.password,
    };
    setEmployeesState((es) => [...es, newEmp]);
    setBalancesState((bs) => ({ ...bs, [id]: { "Annual Leave": 15, "Sick Leave": 10, "Family Responsibility Leave": 3 } }));
    setCurrentUserId(id);
    setViewMode("role");
    setScreen("app");
  };

  const handleLogout = () => { setCurrentUserId(null); setScreen("login"); setViewMode("role"); };

  const handleSaveProfile = (fields) => {
    setEmployeesState((es) => es.map((e) => (e.id === currentUserId ? { ...e, ...fields } : e)));
  };
  const handleChangePassword = (newPassword) => {
    setEmployeesState((es) => es.map((e) => (e.id === currentUserId ? { ...e, password: newPassword } : e)));
  };

  if (screen === "login") return <LoginScreen onLogin={handleLogin} goSignup={() => setScreen("signup")} />;
  if (screen === "signup") return <SignupScreen onSignup={handleSignup} goLogin={() => setScreen("login")} />;

  const loginEmp = employeesState.find((e) => e.id === currentUserId);
  const role = loginEmp.role;

  const decideLeave = (id, approve, signature, reason) =>
    setLeaveRequests((rs) => rs.map((r) => (r.id === id ? {
      ...r,
      status: approve ? "Approved" : "Rejected",
      deciderId: loginEmp.id,
      deciderName: loginEmp.name,
      deciderSignature: signature,
      deciderSignedAt: new Date().toISOString(),
      decisionReason: approve ? null : reason,
    } : r)));
  const addLeaveRequest = (r) => setLeaveRequests((rs) => [r, ...rs]);
  const addSupportTicket = (t) => setSupportTickets((ts) => [t, ...ts]);
  const advanceStage = () => { const i = STAGES.indexOf(payrollStage); if (i < STAGES.length - 1) setPayrollStage(STAGES[i + 1]); };

  const hrNav = [
    { id: "dashboard", label: "Dashboard", icon: LayoutDashboard }, { id: "employees", label: "Employees", icon: Users },
    { id: "payroll", label: "Payroll", icon: Banknote }, { id: "leave", label: "Leave", icon: CalendarDays },
  ];
  const adminNav = [
    { id: "overview", label: "Overview", icon: LayoutDashboard }, { id: "settings", label: "Company & Settings", icon: SlidersHorizontal },
    { id: "levels", label: "Levels & Departments", icon: Building2 }, { id: "users", label: "User Accounts", icon: ShieldCheck },
    { id: "support", label: "Support Tickets", icon: LifeBuoy },
  ];
  const roleTitle = { admin: "Admin", hr: "HR", manager: "Manager", employee: "Employee" }[role];

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
          <button onClick={() => setViewMode((v) => (v === "selfService" ? "role" : "selfService"))} style={{
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

        {viewMode === "role" && role === "manager" && <div style={{ fontSize: 12, color: "#C9BFBC", padding: "10px 6px" }}>Viewing your team's dashboard.</div>}

        <div style={{ marginTop: "auto", paddingTop: 14, borderTop: "1px solid rgba(255,255,255,0.1)" }}>
          <button onClick={() => setViewMode("support")} style={{ display: "flex", alignItems: "center", gap: 8, color: viewMode === "support" ? "#fff" : "#C9BFBC", fontSize: 12.5, padding: "7px 6px", background: viewMode === "support" ? "rgba(255,255,255,0.08)" : "transparent", border: "none", borderRadius: 6, width: "100%", cursor: "pointer", fontWeight: 600 }}>
            <LifeBuoy size={14} /> Support
          </button>
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
        {viewMode === "support" && <SupportCenter emp={loginEmp} tickets={supportTickets} onSubmit={addSupportTicket} onBack={() => setViewMode("role")} isAdminView={false} />}

        {viewMode === "selfService" && role !== "employee" && (
          <EmployeeView emp={loginEmp} leaveRequests={leaveRequests} addLeaveRequest={addLeaveRequest} history={history} setPayslipView={setPayslipView} />
        )}

        {viewMode === "role" && role === "hr" && hrTab === "dashboard" && <HrDashboard leaveRequests={leaveRequests} payrollStage={payrollStage} advanceStage={advanceStage} />}
        {viewMode === "role" && role === "hr" && hrTab === "employees" && <HrEmployees onOpenProfile={setProfileEmp} />}
        {viewMode === "role" && role === "hr" && hrTab === "payroll" && <HrPayroll payrollStage={payrollStage} setPayslipView={setPayslipView} />}
        {viewMode === "role" && role === "hr" && hrTab === "leave" && <HrLeave leaveRequests={leaveRequests} decider={loginEmp} onDecide={decideLeave} />}

        {viewMode === "role" && role === "admin" && adminTab === "overview" && <AdminOverview supportTickets={supportTickets} />}
        {viewMode === "role" && role === "admin" && adminTab === "settings" && <AdminCompanySettings />}
        {viewMode === "role" && role === "admin" && adminTab === "levels" && <AdminLevels />}
        {viewMode === "role" && role === "admin" && adminTab === "users" && <AdminUsers currentUserId={currentUserId} />}
        {viewMode === "role" && role === "admin" && adminTab === "support" && <SupportCenter emp={loginEmp} tickets={supportTickets} onSubmit={addSupportTicket} isAdminView={true} />}

        {viewMode === "role" && role === "manager" && <ManagerView manager={loginEmp} leaveRequests={leaveRequests} onDecide={decideLeave} allEmployees={employeesState} />}

        {viewMode === "role" && role === "employee" && <EmployeeView emp={loginEmp} leaveRequests={leaveRequests} addLeaveRequest={addLeaveRequest} history={history} setPayslipView={setPayslipView} />}
      </div>

      {profileEmp && <ProfileDrawer emp={profileEmp} onClose={() => setProfileEmp(null)} />}
      {payslipView && <Payslip {...payslipView} onClose={() => setPayslipView(null)} />}
    </div>
  );
}
