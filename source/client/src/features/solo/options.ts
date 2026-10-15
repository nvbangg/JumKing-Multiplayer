export interface Options {
	sound: boolean;
	displayTimer: boolean;
	fly: boolean;
}
const key = "jumpking.options";
export function readOptions(): Options {
	try {
		const stored = JSON.parse(localStorage.getItem(key) || "{}");
		return {
			sound: typeof stored.sound === "boolean" ? stored.sound : true,
			displayTimer: stored.displayTimer === true,
			fly: stored.fly === true,
		};
	} catch {
		return { sound: true, displayTimer: false, fly: false };
	}
}
export function saveOptions(options: Options): void {
	localStorage.setItem(key, JSON.stringify(options));
}
