'use strict';

import { workspace, ExtensionContext, window, Disposable } from 'vscode';
import { TransportKind, LanguageClient, LanguageClientOptions, ServerOptions, ErrorAction, CloseAction } from 'vscode-languageclient';
import * as path from 'path'
import * as fs from 'fs'
import TelemetryReporter from 'vscode-extension-telemetry';
import * as jvmDetector from './requirements';

interface LanguageConfiguration {
	vscodeName: string,
	miksiloName?: string,
}
const languages: Array<LanguageConfiguration> = [
    {
         vscodeName: "typeless",
    }
]
let reporter: TelemetryReporter;

export function activate(context: ExtensionContext) {
    createReporter(context)
	reporter.sendTelemetryEvent("activate")

	workspace.onDidChangeConfiguration(() => activateWithConfig(context));
	activateWithConfig(context);
}

function createReporter(context: ExtensionContext) {
    const extensionPath = path.join(context.extensionPath, "package.json");
    const packageFile = JSON.parse(fs.readFileSync(extensionPath, 'utf8'));

	let version = "unknown"
	let extensionId = "unknown"

    if (packageFile) {
		version = packageFile.version;
		extensionId = packageFile.name
	}

	// create telemetry reporter on extension activation
	reporter = new TelemetryReporter(extensionId, version, "4f5e6451-3d46-49f6-a295-ade5e6d47d47");
}

let previousMode: Mode | undefined = undefined;
async function activateWithConfig(context: ExtensionContext) {

	const mode = await getMode()
	if (!mode) {
		window.showErrorMessage("Could not locate a language server. Please configure \"miksilo.jar\" in settings.");
		return;
	}

	if (mode?.toString() === previousMode?.toString())
		return;

	previousMode = mode;

	for(const previousClient of context.subscriptions) {
		previousClient.dispose()
	}
	context.subscriptions.length = 0;

	// ensure it gets property disposed
	context.subscriptions.push(reporter);

	for(const language of languages) {
		const disposable = activateLanguage(mode, language);
		context.subscriptions.push(disposable);
	}
}

abstract class Mode {
    constructor(readonly reason: string) {}
    abstract createServerOptions(args: Array<string>): ServerOptions
}

class JVMMode extends Mode {

    constructor(readonly jvmData: jvmDetector.RequirementsData, readonly jar: string, reason: string = "") {
        super(reason);
    }

    createServerOptions(args: Array<string>): ServerOptions {
        return {
            command: this.getExecutable(),
            options: {
                env: process.env,
                stdio: 'pipe'
            },
            args: ["-jar", this.jar].concat(args)
        }
    }

    toString() {
        return `JVM at ${this.getExecutable()} with jar ${this.jar}`;
    }

    private getExecutable() {
        const javaHome = this.jvmData.java_home;
        return path.join(javaHome, "/bin/java");
    }
}

class JSMode extends Mode {
    constructor(readonly program: string, reason: string = "") {
        super(reason);
    }

    createServerOptions(args: Array<string>): ServerOptions {
        return {
            module: this.program,
            args: args,
            transport: TransportKind.stdio,
            options: {
                env: process.env
            }
        }
    }

    toString() {
        return "Node with program " + this.program;
    }
}

async function getMode(): Promise<Mode | undefined> {
	const jvmData = await jvmDetector.resolveRequirements().catch(_ => null);
    if (!jvmData) {
        reporter.sendTelemetryEvent("javaNotFound")
    }

    const forceNode = workspace.getConfiguration('miksilo').get("forceNode")
    if (!forceNode && jvmData && process.env.MIKSILO) {
        return new JVMMode(jvmData, process.env.MIKSILO, "JVM language server passed in environment variable MIKSILO.");
    }

    if (process.env.JSMIKSILO) {

        return new JSMode(process.env.JSMIKSILO, "Node language server passed in environment variable JSMIKSILO.");
    }

	const settingsJar: string = workspace.getConfiguration('miksilo').get("jar")
	if (!forceNode && jvmData && settingsJar) {
	    return new JVMMode(jvmData, settingsJar, "Miksilo jar specified in settings.");
	}

	const jar: string = `${__dirname}/LanguageServer.jar`;
	if (fs.existsSync(jar)) {
	    return new JVMMode(jvmData, jar, `Found built-in jar.`);
	}

	const nodeProgram: string = workspace.getConfiguration('miksilo').get("js") || `${__dirname}/LanguageServer.js`
	if (nodeProgram) {
	    return new JSMode(nodeProgram)
	}
	return undefined
}

function activateLanguage(mode: Mode, language: LanguageConfiguration): Disposable {
	let serverOptions: ServerOptions = prepareExecutable(mode, language)
	
	let clientOptions: LanguageClientOptions = {
		documentSelector: [{scheme: 'file', language: language.vscodeName}],
		synchronize: {
			configurationSection: 'miksilo',
		},
		errorHandler:  {
			error(error: Error) {
				reporter.sendTelemetryException(error)
				return ErrorAction.Shutdown
			},
			closed() { 
				return CloseAction.Restart 
			}
		}
	}
	
	const start = Date.now()
	const languageClient = new LanguageClient(
		'miksilo' + language.vscodeName, 
		language.vscodeName,
		serverOptions, clientOptions);

	const info = (message: String) => {
		languageClient.outputChannel.appendLine(`[INFO] ${message}`);
	}
	if (mode.reason) {
	    info(mode.reason);
	}
	languageClient.onReady().then(_ => {
		const connectionTime = Date.now() - start;
		info(`Connection time was ${connectionTime}`);
		reporter.sendTelemetryEvent(language.vscodeName + "_ready", undefined, { connectionTime })
	})
	languageClient.onTelemetry((data: any) => {
		const {name, value} = data
		const measurements = {}
		measurements[name] = value
		info(`${name} was ${value}`);
		reporter.sendTelemetryEvent(language.vscodeName + "_lspServer", undefined, measurements)
	})

	info("Using Miksilo mode " + mode);
	return languageClient.start();

}

function prepareExecutable(mode: Mode, language: LanguageConfiguration): ServerOptions {

	language.miksiloName = language.miksiloName || language.vscodeName;
    const args = [language.miksiloName]
	const serverOptions = mode.createServerOptions(args);

	return serverOptions;
}

export function deactivate() {
	if (reporter) {
		reporter.dispose();
	}
}