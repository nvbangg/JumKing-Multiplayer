import { useCallback, useEffect, useRef, useState } from "react";
import { MenuPanel } from "../components/MenuPanel";
import { AuthForm } from "../features/account/AuthForm";
import { OnlineGame } from "../features/community/OnlineGame";
import {
	duration,
	soloStatistics,
	sumStatistics,
} from "../features/records/format";
import { HistoryDetail } from "../features/records/HistoryDetail";
import { HistoryList } from "../features/records/HistoryList";
import { StatisticsPanel } from "../features/records/StatisticsPanel";
import { roomFinishScreen } from "../features/rooms/ending";
import { Lobby } from "../features/rooms/Lobby";
import { PublicRooms } from "../features/rooms/PublicRooms";
import { Results } from "../features/rooms/Results";
import { ControlsHelp, OptionsButtons } from "../features/solo/OptionsPanel";
import { readOptions, saveOptions } from "../features/solo/options";
import { SoloGame } from "../features/solo/SoloGame";
import {
	currentSolo,
	hasCrown,
	rememberCrown,
	type SoloRun,
	StorageUnavailable,
	soloRuns,
} from "../features/solo/storage";
import { SoloSync, syncSolo } from "../features/solo/sync";
import { loadManifest } from "../game/content/assets";
import { AudioBus } from "../game/presentation/AudioBus";
import { ApiError, api, backendOrigin, clearCsrf } from "../network/api";
import {
	EMPTY_STATS,
	type HistoryItem,
	type Identity,
	type LeaderboardEntry,
	type Statistics,
} from "../network/contracts";
import {
	GameSocket,
	type MatchResult,
	type RoomState,
} from "../network/GameSocket";
import { loadReconnect } from "../network/reconnect";

type Screen =
	| "home"
	| "play"
	| "online"
	| "sign-in"
	| "register"
	| "merge-history"
	| "account"
	| "community"
	| "leaderboard"
	| "history"
	| "detail"
	| "options"
	| "controls"
	| "nickname"
	| "join"
	| "public-rooms"
	| "lobby"
	| "results";
interface SoloLaunch {
	profile: string;
	action: "new" | "continue" | "auto";
	expectedRunId: string | null;
}
const initialIdentity: Identity = {
	authenticated: false,
	guest: false,
	account: null,
};
const routes: Partial<Record<Screen, string>> = {
	home: "/",
	play: "/play",
	history: "/history",
	community: "/community",
	options: "/options",
	account: "/account",
	"sign-in": "/sign-in",
	register: "/register",
};
function locationScreen(): Screen {
	return typeof location === "undefined"
		? "home"
		: (Object.entries(routes).find(
				([, path]) => path === location.pathname,
			)?.[0] as Screen | undefined) || "home";
}
function restoredLaunch(): SoloLaunch {
	const saved =
		typeof window !== "undefined" ? window.history.state?.solo : null;
	return {
		profile: typeof saved?.profile === "string" ? saved.profile : "guest",
		action: "auto",
		expectedRunId: null,
	};
}

export function App() {
	const [screen, setScreenState] = useState<Screen>(locationScreen);
	const setScreen = (next: Screen) => {
		if (routes[next] && location.pathname !== routes[next])
			window.history.pushState(null, "", routes[next]);
		setScreenState(next);
	};
	const [identity, setIdentityState] = useState<Identity>(initialIdentity);
	const identityRevision = useRef(0);
	const setIdentity = useCallback((value: Identity) => {
		identityRevision.current++;
		setIdentityState(value);
	}, []);
	const [activeSolo, setActiveSolo] = useState<SoloRun | null>(null);
	const [launch, setLaunch] = useState<SoloLaunch>(restoredLaunch);
	const [statistics, setStatistics] = useState<Statistics>({ ...EMPTY_STATS });
	const [detail, setDetail] = useState<HistoryItem | null>(null);
	const [detailLocal, setDetailLocal] = useState(false);
	const [communityRun, setCommunityRun] = useState<{ id: string } | null>(null);
	const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([]);
	const [leaderboardLoading, setLeaderboardLoading] = useState(false);
	const [room, setRoom] = useState<RoomState | null>(null);
	const [result, setResult] = useState<MatchResult | null>(null);
	const resultRef = useRef<MatchResult | null>(null);
	const viewedEnding = useRef<string | null>(null);
	const [roomIntent, setRoomIntent] = useState<{ roomId?: string }>({});
	const [options, setOptions] = useState(readOptions);
	const [flyAllowed, setFlyAllowed] = useState(__SOLO_FLY_ENABLED__);
	const [error, setError] = useState("");
	const [pending, setPending] = useState(false);
	const [recordsRevision, setRecordsRevision] = useState(0);
	const [syncError, setSyncError] = useState("");
	const sync = useRef<SoloSync | null>(null);
	const socket = useRef<GameSocket | null>(null);
	const connect = useRef<(() => Promise<GameSocket>) | null>(null);
	const accountId = identity.account?.id;
	const profile = identity.account
		? backendOrigin() + ":account:" + identity.account.id
		: "guest";
	const reconnectProfile = identity.account
		? profile
		: (typeof location === "undefined" ? "" : backendOrigin()) + ":guest";
	const playing = screen === "play" || screen === "online";
	useEffect(() => {
		if (identity.account?.crownUnlocked || identity.crownUnlocked)
			void rememberCrown(profile).catch((cause) => setSyncError(cause.message));
	}, [profile, identity.account?.crownUnlocked, identity.crownUnlocked]);
	useEffect(() => {
		setSyncError("");
		if (!accountId) return;
		const worker = new SoloSync(
			profile,
			accountId,
			() => setRecordsRevision((value) => value + 1),
			setSyncError,
		);
		sync.current = worker;
		return () => {
			worker.stop();
			if (sync.current === worker) sync.current = null;
		};
	}, [profile, accountId]);
	useEffect(() => {
		if (screen === "home" || screen === "history") sync.current?.request(true);
	}, [screen]);
	useEffect(() => {
		if (playing || !options.sound) return;
		let disposed = false,
			music: AudioBus | undefined;
		const controller = new AbortController();
		void loadManifest(controller.signal)
			.then((manifest) => {
				if (disposed) return;
				music = new AudioBus(manifest, true, setError, false);
				music.setMusic("audio/music/menu loop/menu_loop");
			})
			.catch(() => {
				if (!disposed) setError("Menu sound could not be loaded");
			});
		return () => {
			disposed = true;
			controller.abort();
			music?.dispose();
		};
	}, [playing, options.sound]);
	// biome-ignore lint/correctness/useExhaustiveDependencies: Profile đổi phải xóa dữ liệu hiển thị của identity trước.
	useEffect(() => {
		setActiveSolo(null);
		setCommunityRun(null);
		setStatistics({ ...EMPTY_STATS });
		setDetail(null);
	}, [profile]);
	const back = () => {
		const next =
			screen === "controls"
				? "options"
				: screen === "leaderboard"
					? "community"
					: screen === "detail"
						? "history"
						: "home";
		if (routes[next]) window.history.replaceState(null, "", routes[next]);
		setError("");
		setScreenState(next);
	};
	const execute = async (action: () => Promise<void>) => {
		if (pending) return;
		setPending(true);
		setError("");
		try {
			await action();
		} catch (cause) {
			setError(
				cause instanceof Error
					? cause.message
					: "Unable to complete this action",
			);
		} finally {
			setPending(false);
		}
	};

	useEffect(() => {
		let disposed = false;
		const expectedIdentityRevision = identityRevision.current;
		const pop = () => {
			setLaunch(restoredLaunch());
			setScreenState(locationScreen());
		};
		window.addEventListener("popstate", pop);
		void api<Identity>("/auth/me")
			.then((value) => {
				if (
					typeof value.authenticated !== "boolean" ||
					typeof value.guest !== "boolean"
				)
					return;
				if (!disposed && identityRevision.current === expectedIdentityRevision)
					setIdentity(value);
			})
			.catch(() => {});
		void api<{ features: { flyEnabled: boolean } }>("/game-config")
			.then((config) => {
				if (!disposed)
					setFlyAllowed(__SOLO_FLY_ENABLED__ || config.features.flyEnabled);
			})
			.catch(() => {});
		return () => {
			disposed = true;
			window.removeEventListener("popstate", pop);
			socket.current?.close();
		};
	}, [setIdentity]);
	useEffect(() => {
		if (!identity.authenticated && !identity.guest) return;
		let cancelled = false;
		void loadReconnect(reconnectProfile)
			.then(async (context) => {
				if (!context || cancelled) return;
				await connect.current?.();
			})
			.catch((cause) => {
				if (!cancelled)
					setError(cause instanceof Error ? cause.message : "Reconnect failed");
			});
		return () => {
			cancelled = true;
		};
	}, [reconnectProfile, identity.authenticated, identity.guest]);

	// biome-ignore lint/correctness/useExhaustiveDependencies: Revision là tín hiệu tải lại khi Solo sync hoàn tất.
	useEffect(() => {
		if (screen === "play" || screen === "online" || screen === "lobby") return;
		let disposed = false;
		const local = soloRuns(profile)
			.then((runs) => {
				const total = soloStatistics(runs);
				if (!disposed) {
					setActiveSolo(runs.find((run) => run.status === "ACTIVE") || null);
					if (!identity.authenticated) setStatistics(total);
				}
				return total;
			})
			.catch((cause) => {
				if (!disposed)
					setError(
						cause instanceof Error
							? cause.message
							: "Browser storage is unavailable",
					);
				return { ...EMPTY_STATS };
			});
		if (identity.authenticated || identity.guest) {
			void api<{ total: Statistics }>("/profile/me")
				.then(async ({ total }) => {
					const solo = await local;
					if (!disposed)
						setStatistics(
							identity.authenticated ? total : sumStatistics(total, solo),
						);
				})
				.catch(() => {});
		}
		if (screen === "community" && identity.authenticated)
			void api<{ run: { id: string } | null }>("/community/current")
				.then((result) => {
					if (!disposed) setCommunityRun(result.run);
				})
				.catch((cause) => {
					if (!disposed) setError(cause.message);
				});
		if (screen === "leaderboard") {
			setLeaderboardLoading(true);
			void api<{ entries: LeaderboardEntry[] }>("/leaderboard?limit=100")
				.then((result) => {
					if (!disposed) setLeaderboard(result.entries);
				})
				.catch((cause) => {
					if (!disposed) setError(cause.message);
				})
				.finally(() => {
					if (!disposed) setLeaderboardLoading(false);
				});
		}
		return () => {
			disposed = true;
		};
	}, [
		profile,
		screen,
		identity.authenticated,
		identity.guest,
		recordsRevision,
	]);

	const openSolo = (action: "new" | "continue") => {
		void execute(async () => {
			if (identity.account?.crownUnlocked) await rememberCrown(profile);
			const current = await currentSolo(profile).catch((cause) => {
				if (
					action === "new" &&
					cause instanceof StorageUnavailable &&
					window.confirm(
						"Browser storage is unavailable. Start without saving?",
					)
				)
					return null;
				throw cause;
			});
			if (
				action === "new" &&
				current &&
				!window.confirm("Replace your unfinished Solo game?")
			)
				return;
			window.history.pushState({ solo: { profile } }, "", "/play");
			setLaunch({ profile, action, expectedRunId: current?.id || null });
			setScreenState("play");
		});
	};
	const connectedSocket = async () => {
		if (
			!socket.current &&
			identity.account &&
			(await hasCrown(profile).catch(() => false))
		)
			await syncSolo(profile, identity.account.id);
		if (!identity.authenticated && !identity.guest)
			setIdentity(await api<Identity>("/auth/guest", "POST"));
		if (
			!socket.current &&
			!identity.authenticated &&
			(await hasCrown("guest").catch(() => false))
		)
			await api("/profile/crown", "POST");
		if (!socket.current)
			socket.current = new GameSocket(
				(type, payload) => {
					if (type === "ROOM_STATE") {
						const next = payload as unknown as RoomState;
						if (!next.roomId) {
							setRoom(null);
							setScreen("home");
							return;
						}
						setRoom(next);
						if (next.kind === "COMMUNITY") {
							setScreen("online");
							return;
						}
						if (next.result && typeof next.result === "object") {
							resultRef.current = next.result as MatchResult;
							setResult(next.result as MatchResult);
						}
						if (next.state === "PLAYING" || next.state === "PAUSED") {
							setScreen("online");
							return;
						}
						const self = next.members?.find(
							(member) => member.playerId === socket.current?.selfId,
						);
						setScreen(
							resultRef.current &&
								self?.nextMatchConfirmed !== true &&
								(next.state === "WAITING" || next.state === "FINISHED")
								? roomFinishScreen(
										resultRef.current,
										socket.current?.selfId || null,
										viewedEnding.current,
									)
								: "lobby",
						);
					} else if (type === "GAME_FINISHED") {
						const finished = payload as unknown as MatchResult;
						resultRef.current = finished;
						setResult(finished);
						setScreen(
							roomFinishScreen(
								finished,
								socket.current?.selfId || null,
								viewedEnding.current,
							),
						);
					} else if (type === "GAME_STARTED") {
						resultRef.current = null;
						setResult(null);
					} else if (
						type === "SAVE_STATUS" &&
						resultRef.current !== null &&
						resultRef.current.playId === payload.playId &&
						["PENDING", "SAVED", "FAILED"].includes(String(payload.saveStatus))
					) {
						const updated = {
							...resultRef.current,
							saveStatus: payload.saveStatus as MatchResult["saveStatus"],
						};
						resultRef.current = updated;
						setResult(updated);
						setScreen(
							roomFinishScreen(
								updated,
								socket.current?.selfId || null,
								viewedEnding.current,
							),
						);
					} else if (
						type === "PLAYER_FINISHED" &&
						payload.playerId === socket.current?.selfId &&
						payload.showEnding === true
					) {
						void rememberCrown(profile).catch((cause) =>
							setError(cause.message),
						);
					} else if (type === "ROOM_LEFT") {
						setRoom(null);
						setResult(null);
						resultRef.current = null;
						socket.current?.close();
						socket.current = null;
						back();
					} else if (type === "ERROR") setError(String(payload.message));
				},
				() => setError("Disconnected from the game server"),
			);
		await socket.current.restore(reconnectProfile);
		return socket.current;
	};
	connect.current = connectedSocket;
	const command = (type: string, payload: Record<string, unknown> = {}) => {
		void execute(async () => {
			const connection = await connectedSocket();
			if (
				type === "JOIN_COMMUNITY" &&
				connection.selfId &&
				room?.kind === "COMMUNITY"
			) {
				await connection.command(
					payload.newRun === true ? "NEW_COMMUNITY_RUN" : "GET_ROOM_STATE",
					payload.newRun === true
						? { expectedRunId: payload.expectedRunId }
						: {},
				);
			} else await connection.command(type, payload);
		});
	};
	const updateOptions = (key: keyof typeof options) => {
		const next = { ...options, [key]: !options[key] };
		setOptions(next);
		try {
			saveOptions(next);
			setError("");
		} catch {
			setError("Options could not be saved on this device");
		}
	};

	if (screen === "play")
		return (
			<SoloGame
				{...launch}
				onExit={() => {
					setOptions(readOptions());
					back();
				}}
			/>
		);
	if (screen === "online" && room && socket.current)
		return (
			<OnlineGame
				room={room}
				socket={socket.current}
				onResults={() => {
					viewedEnding.current = resultRef.current?.playId || null;
					setScreen("results");
				}}
				onHome={() => {
					socket.current?.close();
					socket.current = null;
					setRoom(null);
					setScreen("home");
					clearCsrf();
					const expectedIdentityRevision = identityRevision.current;
					void api<Identity>("/auth/me")
						.then((value) => {
							if (identityRevision.current === expectedIdentityRevision)
								setIdentity(value);
						})
						.catch((cause) => {
							if (identityRevision.current !== expectedIdentityRevision) return;
							if (
								cause instanceof ApiError &&
								["SESSION_EXPIRED", "UNAUTHENTICATED"].includes(cause.code)
							)
								setIdentity(initialIdentity);
							else
								setError(
									cause instanceof Error
										? cause.message
										: "Identity could not be refreshed",
								);
						});
				}}
			/>
		);
	return (
		<main className="home">
			<header className="brand">
				<h1>
					<img src="/assets/title_logo.png" alt="JumpKing" />
				</h1>
			</header>
			<div className={screen === "home" ? "home-layout" : "single-panel"}>
				<MenuPanel
					title={
						screen === "home"
							? undefined
							: (
									{
										"sign-in": "Sign In",
										register: "Create Account",
										"merge-history": "Guest Progress",
										account: "Account",
										community: "Community Tower",
										leaderboard: "Leaderboard",
										history: "History",
										detail: "Game Details",
										options: "Options",
										controls: "Controls",
										nickname: roomIntent.roomId
											? "Join Room"
											: "Play with Friends",
										join: "Join by ID",
										"public-rooms": "Join Public",
										lobby:
											room?.kind === "COMMUNITY" ? "Community Tower" : "Lobby",
										results: "Results",
									} as Partial<Record<Screen, string>>
								)[screen]
					}
					onBack={
						screen === "home" || screen === "lobby" || screen === "results"
							? undefined
							: back
					}
				>
					{screen === "home" && (
						<>
							{activeSolo && (
								<button
									type="button"
									onClick={() => openSolo("continue")}
									disabled={pending}
								>
									Continue Solo
								</button>
							)}
							<button
								type="button"
								onClick={() => openSolo("new")}
								disabled={pending}
							>
								New Solo Game
							</button>
							<button
								type="button"
								onClick={() => {
									setRoomIntent({});
									setScreen("nickname");
								}}
							>
								Play with Friends
							</button>
							<button
								type="button"
								onClick={() => {
									setError("");
									setScreen("public-rooms");
								}}
							>
								Join Public
							</button>
							<button type="button" onClick={() => setScreen("join")}>
								Join by ID
							</button>
							<button type="button" onClick={() => setScreen("community")}>
								Community Tower
							</button>
							<button type="button" onClick={() => setScreen("history")}>
								History
							</button>
							<button type="button" onClick={() => setScreen("options")}>
								Options
							</button>
							<div className="account-actions">
								{identity.account ? (
									<button type="button" onClick={() => setScreen("account")}>
										{identity.account.username}
									</button>
								) : (
									<>
										<button type="button" onClick={() => setScreen("sign-in")}>
											Sign In
										</button>
										<button type="button" onClick={() => setScreen("register")}>
											Create Account
										</button>
									</>
								)}
							</div>
						</>
					)}
					{(screen === "sign-in" || screen === "register") && (
						<AuthForm
							key={screen}
							register={screen === "register"}
							onSuccess={(value) => {
								setIdentity(value);
								if (value.guestHistoryAvailable) setScreen("merge-history");
								else back();
							}}
							onSwitch={() =>
								setScreen(screen === "register" ? "sign-in" : "register")
							}
						/>
					)}
					{screen === "merge-history" && (
						<>
							<p>
								Add this Guest's Room history and earned crown to your account?
							</p>
							<button
								type="button"
								disabled={pending}
								onClick={() => {
									void execute(async () => {
										const result = await api<{ merged: boolean }>(
											"/auth/guest-history/merge",
											"POST",
										);
										if (!result.merged)
											throw new Error("History could not be merged");
										setIdentity(await api<Identity>("/auth/me"));
										back();
									});
								}}
							>
								Merge Guest Progress
							</button>
							<button type="button" disabled={pending} onClick={back}>
								Skip
							</button>
						</>
					)}
					{screen === "account" && identity.account && (
						<>
							<dl className="account-info">
								<dt>Username</dt>
								<dd>{identity.account.username}</dd>
								<dt>Email</dt>
								<dd>{identity.account.email}</dd>
							</dl>
							<button
								type="button"
								disabled={pending}
								onClick={() => {
									void execute(async () => {
										await api("/auth/logout", "POST");
										clearCsrf();
										setIdentity(initialIdentity);
										setIdentity(
											await api<Identity>("/auth/me").catch(
												() => initialIdentity,
											),
										);
										back();
									});
								}}
							>
								Sign Out
							</button>
						</>
					)}
					{screen === "community" && (
						<>
							{!identity.authenticated ? (
								<>
									<p>Sign in to play Community Tower.</p>
									<button type="button" onClick={() => setScreen("sign-in")}>
										Sign In
									</button>
									<button type="button" onClick={() => setScreen("register")}>
										Create Account
									</button>
								</>
							) : (
								<>
									{communityRun && (
										<button
											type="button"
											disabled={pending}
											onClick={() => command("JOIN_COMMUNITY")}
										>
											Continue
										</button>
									)}
									<button
										type="button"
										disabled={pending}
										onClick={() => {
											if (
												!communityRun ||
												window.confirm(
													"Replace your unfinished Community game?",
												)
											)
												command("JOIN_COMMUNITY", {
													newRun: true,
													expectedRunId: communityRun?.id || null,
												});
										}}
									>
										New Game
									</button>
								</>
							)}
							<button type="button" onClick={() => setScreen("leaderboard")}>
								Leaderboard
							</button>
						</>
					)}
					{screen === "leaderboard" && (
						<>
							{leaderboardLoading ? (
								<p role="status">Loading…</p>
							) : (
								<ol className="leaderboard" aria-label="Community leaderboard">
									{leaderboard.map((entry) => (
										<li key={entry.id}>
											<span>
												{entry.rank}. {entry.username}
											</span>
											<strong>
												{entry.finishTimeMs === null
													? Math.floor(entry.maxProgress) + " px"
													: duration(entry.finishTimeMs)}
											</strong>
										</li>
									))}
								</ol>
							)}
							{!leaderboardLoading && !leaderboard.length && !error && (
								<p>No results yet.</p>
							)}
						</>
					)}
					{screen === "history" && (
						<HistoryList
							profile={profile}
							authenticated={identity.authenticated}
							hasIdentity={identity.authenticated || identity.guest}
							revision={recordsRevision}
							onSelect={(item, local) => {
								setDetail(item);
								setDetailLocal(local);
								setScreen("detail");
							}}
						/>
					)}
					{screen === "detail" && detail && (
						<HistoryDetail
							key={detail.kind + ":" + detail.id}
							item={detail}
							local={detailLocal}
						/>
					)}
					{screen === "options" && (
						<OptionsButtons
							options={options}
							flyAllowed={flyAllowed}
							onChange={updateOptions}
							onControls={() => setScreen("controls")}
							onError={setError}
						/>
					)}
					{screen === "controls" && <ControlsHelp flyAllowed={flyAllowed} />}
					{screen === "nickname" && (
						<form
							className="menu-form"
							onSubmit={(event) => {
								event.preventDefault();
								const nickname = String(
									new FormData(event.currentTarget).get("nickname"),
								).trim();
								command(roomIntent.roomId ? "JOIN_ROOM" : "CREATE_ROOM", {
									...(roomIntent.roomId ? { roomId: roomIntent.roomId } : {}),
									nickname,
									...(!roomIntent.roomId
										? {
												visibility: String(
													new FormData(event.currentTarget).get("visibility"),
												),
											}
										: {}),
								});
							}}
						>
							<label>
								Nickname
								<input
									name="nickname"
									defaultValue={identity.account?.username.slice(0, 16) || ""}
									required
									maxLength={16}
								/>
							</label>
							{!roomIntent.roomId && (
								<label>
									Visibility
									<select
										name="visibility"
										aria-label="Visibility"
										defaultValue="PRIVATE"
									>
										<option value="PRIVATE">Private</option>
										<option value="PUBLIC">Public</option>
									</select>
								</label>
							)}
							<button type="submit" disabled={pending}>
								{pending
									? "Connecting…"
									: roomIntent.roomId
										? "Join"
										: "Create Room"}
							</button>
						</form>
					)}
					{screen === "public-rooms" && (
						<PublicRooms
							onJoin={(roomId) => {
								setError("");
								setRoomIntent({ roomId });
								setScreen("nickname");
							}}
						/>
					)}
					{screen === "join" && (
						<form
							className="menu-form"
							onSubmit={(event) => {
								event.preventDefault();
								const roomId = String(
									new FormData(event.currentTarget).get("roomId"),
								)
									.trim()
									.toUpperCase();
								void execute(async () => {
									const found = await api<{
										kind: "ROOM" | "COMMUNITY";
										joinable: boolean;
									}>("/rooms/" + encodeURIComponent(roomId));
									if (!found.joinable)
										throw new Error("This room is not accepting new players");
									if (found.kind === "ROOM") {
										setRoomIntent({ roomId });
										setScreen("nickname");
									} else {
										if (!identity.authenticated)
											throw new Error("Sign in to join Community Tower");
										const connection = await connectedSocket();
										await connection.command("JOIN_ROOM", { roomId });
									}
								});
							}}
						>
							<label>
								Room ID
								<input
									name="roomId"
									required
									minLength={6}
									maxLength={6}
									autoCapitalize="characters"
									autoComplete="off"
									pattern="[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}"
									onInput={(event) => {
										event.currentTarget.value =
											event.currentTarget.value.toUpperCase();
									}}
								/>
							</label>
							<button type="submit" disabled={pending}>
								{pending ? "Looking up…" : "Join"}
							</button>
						</form>
					)}
					{screen === "lobby" && room && (
						<Lobby
							room={room}
							selfId={socket.current?.selfId || null}
							pending={pending}
							command={command}
							socket={socket.current}
						/>
					)}
					{screen === "results" && result && (
						<Results
							result={result}
							room={room}
							selfId={socket.current?.selfId || null}
							pending={pending}
							onAgain={() => command("PLAY_AGAIN", { matchId: result.playId })}
							onLeave={() => command("LEAVE_ROOM")}
						/>
					)}
					{error && (
						<p className="error" role="alert">
							{error}
						</p>
					)}
					{syncError && (screen === "home" || screen === "history") && (
						<p role="status">Solo sync pending: {syncError}</p>
					)}
				</MenuPanel>
				{screen === "home" && <StatisticsPanel statistics={statistics} />}
			</div>
		</main>
	);
}
