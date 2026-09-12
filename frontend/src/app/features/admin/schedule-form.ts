/**
 * The Reschedule form, and its conversion to and from the standard (Spring) cron the server
 * stores: six fields, seconds first, in Asia/Dhaka time. The server checks every schedule
 * again before saving it, so nothing here has to be the last word.
 */

export type ScheduleType = 'HOURLY' | 'DAILY' | 'WEEKLY' | 'MONTHLY';

export interface ScheduleForm {
  type: ScheduleType;
  /** Hourly: minutes between runs -- 30, 60, 120 ... 720. */
  every: number;
  /** Hourly: minute past the hour (a 30-minute repeat also runs 30 minutes later). */
  minute: number;
  /** Hourly: round the clock, or only between fromHour:00 and toHour:59. */
  allDay: boolean;
  fromHour: number;
  toHour: number;
  /** Daily, weekly, monthly: "HH:mm". */
  time: string;
  /** Weekly: SUN ... SAT. */
  days: string[];
  /** Monthly: "1" ... "28", or "L" for the last day. */
  dayOfMonth: string;
}

export const SCHEDULE_TYPES: { value: ScheduleType; label: string }[] = [
  { value: 'HOURLY', label: 'Hourly' },
  { value: 'DAILY', label: 'Daily' },
  { value: 'WEEKLY', label: 'Weekly' },
  { value: 'MONTHLY', label: 'Monthly' },
];

export const EVERY_OPTIONS: { value: number; label: string }[] = [
  { value: 30, label: '30 minutes' },
  { value: 60, label: '1 hour' },
  { value: 120, label: '2 hours' },
  { value: 180, label: '3 hours' },
  { value: 240, label: '4 hours' },
  { value: 360, label: '6 hours' },
  { value: 720, label: '12 hours' },
];

export const DAYS: { value: string; label: string; long: string }[] = [
  { value: 'SUN', label: 'Sun', long: 'Sunday' },
  { value: 'MON', label: 'Mon', long: 'Monday' },
  { value: 'TUE', label: 'Tue', long: 'Tuesday' },
  { value: 'WED', label: 'Wed', long: 'Wednesday' },
  { value: 'THU', label: 'Thu', long: 'Thursday' },
  { value: 'FRI', label: 'Fri', long: 'Friday' },
  { value: 'SAT', label: 'Sat', long: 'Saturday' },
];

export const HOURS: number[] = Array.from({ length: 24 }, (_, h) => h);

/** 1st ... 28th -- every month has those -- and the last day. */
export const MONTH_DAYS: { value: string; label: string }[] = [
  ...Array.from({ length: 28 }, (_, i) => ({ value: String(i + 1), label: ordinal(i + 1) })),
  { value: 'L', label: 'Last day' },
];

const STEPS = [2, 3, 4, 6, 12];

export function pad(n: number): string {
  return String(n).padStart(2, '0');
}

export function ordinal(n: number): string {
  const s = n % 100 >= 11 && n % 100 <= 13 ? 'th' : ['th', 'st', 'nd', 'rd'][n % 10] ?? 'th';
  return `${n}${s}`;
}

/** Minutes offered for an hourly schedule: 5-minute steps, within the half hour for a 30-minute repeat. */
export function minuteOptions(every: number): number[] {
  return Array.from({ length: (every === 30 ? 30 : 60) / 5 }, (_, i) => i * 5);
}

/** What the form shows for a schedule it cannot read: daily at 08:00, a Sun–Thu week. */
export function defaultForm(): ScheduleForm {
  return {
    type: 'DAILY', every: 60, minute: 0, allDay: true, fromHour: 8, toHour: 20,
    time: '08:00', days: ['SUN', 'MON', 'TUE', 'WED', 'THU'], dayOfMonth: '1',
  };
}

/** Why the form cannot be saved yet, in words; null when it can. */
export function formProblem(f: ScheduleForm): string | null {
  if (f.type === 'HOURLY') {
    return !f.allDay && f.fromHour > f.toHour ? 'The end hour must be after the start hour.' : null;
  }
  if (!/^\d{2}:\d{2}$/.test(f.time)) return 'Pick a time.';
  if (f.type === 'WEEKLY' && f.days.length === 0) return 'Pick at least one day.';
  return null;
}

export function formToCron(f: ScheduleForm): string {
  const [hh, mm] = f.time.split(':').map(Number);
  switch (f.type) {
    case 'HOURLY': {
      const window = f.allDay ? null : `${f.fromHour}-${f.toHour}`;
      if (f.every === 30) return `0 ${f.minute % 30}/30 ${window ?? '*'} * * *`;
      if (f.every === 60) return `0 ${f.minute} ${window ?? '*'} * * *`;
      const k = f.every / 60;
      return `0 ${f.minute} ${window ? `${window}/${k}` : `*/${k}`} * * *`;
    }
    case 'DAILY':
      return `0 ${mm} ${hh} * * *`;
    case 'WEEKLY':
      return `0 ${mm} ${hh} * * ${ordered(f.days).join(',')}`;
    case 'MONTHLY':
      return `0 ${mm} ${hh} ${f.dayOfMonth} * *`;
  }
}

/** The form for a schedule, or null when it is a shape the form cannot show. */
export function formFromCron(cron: string): ScheduleForm | null {
  const f = cron.trim().split(/\s+/);
  if (f.length !== 6 || f[0] !== '0' || f[4] !== '*') return null;
  const [, min, hour, domRaw, , dowRaw] = f;
  const dom = domRaw === '?' ? '*' : domRaw.toUpperCase();
  const dow = dowRaw === '?' ? '*' : dowRaw.toUpperCase();
  const base = defaultForm();
  const m = num(min);
  const h = num(hour);
  const at = (hh: number, mm: number) => `${pad(hh)}:${pad(mm)}`;

  if (dom !== '*') {
    const d = dom === 'L' ? 'L' : num(dom);
    if (dow !== '*' || d === null || m === null || h === null || (d !== 'L' && (d < 1 || d > 28))) return null;
    return { ...base, type: 'MONTHLY', dayOfMonth: String(d), time: at(h, m) };
  }
  if (dow !== '*') {
    const days = expandDays(dow);
    return days && m !== null && h !== null ? { ...base, type: 'WEEKLY', days, time: at(h, m) } : null;
  }
  if (m !== null && h !== null) {
    return { ...base, type: 'DAILY', time: at(h, m) };
  }

  // Hourly: "10", or "0/30" for every 30 minutes; hours "*", "8-20", "*/6", "8-20/2".
  let every = 60;
  let minute = m;
  const half = /^(\d{1,2})\/30$/.exec(min);
  if (half) {
    every = 30;
    minute = Number(half[1]);
  }
  if (minute === null || minute % 5 !== 0 || minute > (every === 30 ? 25 : 55)) return null;
  const hourly = { ...base, type: 'HOURLY' as const, every, minute };

  if (hour === '*') return { ...hourly, allDay: true };
  let r = /^(\d{1,2})-(\d{1,2})$/.exec(hour);
  if (r) return { ...hourly, allDay: false, fromHour: Number(r[1]), toHour: Number(r[2]) };
  if (every === 30) return null;
  r = /^(?:\*|0)\/(\d+)$/.exec(hour);
  if (r && STEPS.includes(Number(r[1]))) return { ...hourly, every: Number(r[1]) * 60, allDay: true };
  r = /^(\d{1,2})-(\d{1,2})\/(\d+)$/.exec(hour);
  if (r && STEPS.includes(Number(r[3]))) {
    return { ...hourly, every: Number(r[3]) * 60, allDay: false, fromHour: Number(r[1]), toHour: Number(r[2]) };
  }
  return null;
}

/** A schedule in words: "Every hour, 08:10–20:10", "Sunday to Thursday at 09:00". */
export function describeCron(cron: string): string {
  const f = formFromCron(cron);
  if (!f) return 'Custom schedule';
  switch (f.type) {
    case 'HOURLY': {
      const what = f.every === 30 ? 'Every 30 minutes' : f.every === 60 ? 'Every hour' : `Every ${f.every / 60} hours`;
      if (f.allDay) {
        return f.every === 30 ? `${what}, at :${pad(f.minute)} and :${pad(f.minute + 30)}` : `${what}, at :${pad(f.minute)}`;
      }
      const stepHours = Math.max(1, f.every / 60);
      const lastHour = f.fromHour + Math.floor((f.toHour - f.fromHour) / stepHours) * stepHours;
      const lastMinute = f.every === 30 ? f.minute + 30 : f.minute;
      return `${what}, ${pad(f.fromHour)}:${pad(f.minute)}–${pad(lastHour)}:${pad(lastMinute)}`;
    }
    case 'DAILY':
      return `Daily at ${f.time}`;
    case 'WEEKLY':
      return `${daysInWords(f.days)} at ${f.time}`;
    case 'MONTHLY':
      return `Monthly on the ${f.dayOfMonth === 'L' ? 'last day' : ordinal(Number(f.dayOfMonth))} at ${f.time}`;
  }
}

function daysInWords(days: string[]): string {
  const idx = ordered(days).map((d) => DAYS.findIndex((x) => x.value === d));
  if (idx.length === 7) return 'Every day';
  const consecutive = idx.length > 2 && idx.every((v, i) => i === 0 || v === idx[i - 1] + 1);
  if (consecutive) return `${DAYS[idx[0]].long} to ${DAYS[idx[idx.length - 1]].long}`;
  return 'Weekly on ' + idx.map((i) => DAYS[i].label).join(', ');
}

function ordered(days: string[]): string[] {
  return DAYS.map((d) => d.value).filter((d) => days.includes(d));
}

/** "MON-FRI", "SUN,TUE", "1-5" -> day codes in week order; null for anything else. */
function expandDays(dow: string): string[] | null {
  const codes = DAYS.map((d) => d.value);
  const toIndex = (t: string): number | null => {
    if (/^\d$/.test(t)) return Number(t) % 7; // 0 and 7 are both Sunday
    const i = codes.indexOf(t);
    return i < 0 ? null : i;
  };
  const out = new Set<number>();
  for (const part of dow.split(',')) {
    const [a, b] = part.split('-');
    const from = toIndex(a);
    const to = b === undefined ? from : toIndex(b);
    if (from === null || to === null || to < from) return null;
    for (let i = from; i <= to; i++) out.add(i);
  }
  return [...out].sort((x, y) => x - y).map((i) => codes[i]);
}

function num(s: string): number | null {
  return /^\d{1,2}$/.test(s) ? Number(s) : null;
}
