export default {
	testDir: '.',
	testMatch: '**/*.spec.mjs',
	timeout: 60_000,
	use: {
		browserName: 'chromium',
		headless: true,
		trace: 'retain-on-failure'
	},
	workers: 1
};
