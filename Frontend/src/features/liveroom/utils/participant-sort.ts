import type { Participant } from "../types";


export function sortParticipants(participants: Participant[]): Participant[] {
  return [...participants].sort((a, b) => {
    if (a.roomRole !== b.roomRole) return a.roomRole === "OWNER" ? -1 : 1;
    const byTime = Date.parse(a.joinedAt) - Date.parse(b.joinedAt);
    if (!Number.isNaN(byTime) && byTime !== 0) return byTime;
    return a.userId.localeCompare(b.userId);
  });
}

export function displayName(participant: { userEmail: string }): string {
  const at = participant.userEmail.indexOf("@");
  return at > 0 ? participant.userEmail.slice(0, at) : participant.userEmail;
}

export function initialsOf(email: string): string {
  const local = email.includes("@") ? email.slice(0, email.indexOf("@")) : email;
  const parts = local.split(/[._-]+/).filter(Boolean);
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
  return local.slice(0, 2).toUpperCase();
}