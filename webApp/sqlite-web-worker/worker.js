import sqlite3InitModule from '@sqlite.org/sqlite-wasm';

const isDebug = false; // Set to true to enable debug logging, false to disable.

if (isDebug) {
    console.debug = console.log;
} else {
    console.debug = () => {};
}

let sqlite3 = null;
let poolUtil = null;

// Maps to track of active database connections and prepared statements by their unique IDs.
const databases = new Map(); // stores databaseId -> SQLiteDbObject
const statements = new Map(); // stores statementId -> SQLiteStatementObject

// Counters to generate unique IDs for new database connections and statements.
let nextDatabaseId = 0;
let nextStatementId = 0;

const workerId = self.crypto.randomUUID();
const channel = new BroadcastChannel('sqlite-channel');
let isLeader = false;
let leaderReady = false;
const pendingFollowerMessages = [];
const messageQueue = [];

function openRequest(id, requestData, sendResponse) {
    try {
        const newDatabaseId = nextDatabaseId++;
        let fileName = requestData.fileName;
        if (!fileName.startsWith('/')) fileName = '/' + fileName;
        const newDatabase = new poolUtil.OpfsSAHPoolDb(fileName);
        databases.set(newDatabaseId, newDatabase);
        sendResponse({'id': id, data: {'databaseId': newDatabaseId}});
    } catch (error) {
        sendResponse({'id': id, error: error.message});
    }
}

function prepareRequest(id, requestData, sendResponse) {
    try {
        const newStatementId = nextStatementId++;
        const resultData = {
            'statementId': newStatementId,
            'parameterCount': 0,
            'columnNames': []
        };
        const database = databases.get(requestData.databaseId);
        if (!database) {
            sendResponse({'id': id, error: "Invalid database ID: " + requestData.databaseId});
            return;
        }
        const statement = database.prepare(requestData.sql);
        statements.set(newStatementId, statement);
        resultData.parameterCount = sqlite3.capi.sqlite3_bind_parameter_count(statement);
        for (let i = 0; i < statement.columnCount; i++) {
            resultData.columnNames.push(sqlite3.capi.sqlite3_column_name(statement, i));
        }
        sendResponse({'id': id, data: resultData});
    } catch (error) {
        sendResponse({'id': id, error: error.message});
    }
}

function stepRequest(id, requestData, sendResponse) {
    const statement = statements.get(requestData.statementId);
    if (!statement) {
        sendResponse({'id': id, error: "Invalid statement ID: " + requestData.statementId});
        return;
    }
    try {
        const resultData = {
            'rows': [],
            'columnTypes': []
        };
        statement.reset()
        statement.clearBindings()
        for (let i = 0; i < requestData.bindings.length; i++) {
            statement.bind(i + 1, requestData.bindings[i]);
        }
        while (statement.step()) {
            if (!resultData.columnTypes.length) {
                for (let i = 0; i < statement.columnCount; i++) {
                    resultData.columnTypes.push(sqlite3.capi.sqlite3_column_type(statement, i));
                }
            }
            resultData.rows.push(statement.get([]));
        }
        sendResponse({'id': id, data: resultData});
    } catch (error) {
        sendResponse({'id': id, error: error.message});
    }
}

function closeRequest(id, requestData, sendResponse) {
    if (requestData.statementId) {
        const statement = statements.get(requestData.statementId);
        if (!statement) {
            sendResponse({'id': id, error: "Invalid statement ID: " + requestData.statementId});
            return;
        }
        try {
            statement.finalize();
            statements.delete(requestData.statementId);
        } catch (error) {
            sendResponse({'id': id, error: error.message});
        }
    }

    if (requestData.databaseId) {
        const database = databases.get(requestData.databaseId);
        if (!database) {
            sendResponse({'id': id, error: "Invalid database ID: " + requestData.databaseId});
            return;
        }
        try {
            database.close();
            databases.delete(requestData.databaseId);
        } catch (error) {
            sendResponse({'id': id, error: error.message});
        }
    }
}

// A map that links command names (strings) to their respective handler functions.
const commandMap = {
    'open': openRequest,
    'prepare': prepareRequest,
    'step': stepRequest,
    'close': closeRequest,
};

function handleMessageCore(requestMsg, sendResponse) {
    if (!Object.hasOwn(requestMsg, 'data') && requestMsg.data == null) {
        sendResponse({'id': requestMsg.id, 'error': "Invalid request, missing 'data'."});
        return;
    }
    if (!Object.hasOwn(requestMsg.data, 'cmd') && requestMsg.data.cmd == null) {
        sendResponse({'id': requestMsg.id, 'error': "Invalid request, missing 'cmd'."});
        return;
    }
    const command = requestMsg.data.cmd;
    const requestHandler = commandMap[command];
    if (requestHandler) {
        requestHandler(requestMsg.id, requestMsg.data, sendResponse);
    } else {
        sendResponse({'id': requestMsg.id, 'error': "Invalid request, unknown command: '" + command + "'."});
    }
}

channel.onmessage = async (e) => {
    const msg = e.data;
    if (msg.type === 'leader-ready') {
        leaderReady = true;
        while (pendingFollowerMessages.length > 0) {
            const req = pendingFollowerMessages.shift();
            channel.postMessage({ type: 'request', workerId, msg: req });
        }
    } else if (msg.type === 'ping' && isLeader && leaderReady) {
        channel.postMessage({ type: 'leader-ready' });
    } else if (msg.type === 'request' && isLeader && leaderReady) {
        handleMessageCore(msg.msg, (resp) => {
            channel.postMessage({ type: 'response', workerId: msg.workerId, msg: resp });
        });
    } else if (msg.type === 'response' && !isLeader && msg.workerId === workerId) {
        postMessage(msg.msg);
    }
};

function handleMessage(e) {
    const requestMsg = e.data;
    console.debug("handleMessage: " + JSON.stringify(requestMsg));

    if (isLeader) {
        if (leaderReady) {
            handleMessageCore(requestMsg, (resp) => postMessage(resp));
        } else {
            pendingFollowerMessages.push(requestMsg);
        }
    } else {
        if (leaderReady) {
            channel.postMessage({ type: 'request', workerId, msg: requestMsg });
        } else {
            pendingFollowerMessages.push(requestMsg);
            channel.postMessage({ type: 'ping' });
        }
    }
}

onmessage = (e) => {
    if (!sqlite3) {
        messageQueue.push(e);
    } else {
        handleMessage(e);
    }
};

async function acquireLock() {
    console.debug("aquireLock: Attempting to acquire lock...");
    await navigator.locks.request('sqlite-active', {mode: 'exclusive'}, async (lock) => {
        console.debug("aquireLock: Lock acquired, this worker is the leader.");
        isLeader = true;
        leaderReady = false;

        try {
            console.debug("aquireLock: Initializing SQLite and OPFS SAH Pool VFS...");
            poolUtil = await sqlite3.installOpfsSAHPoolVfs({ name: 'opfs-sahpool' });
            leaderReady = true;
            console.debug("aquireLock: Leader is ready, notifying followers...");
            channel.postMessage({ type: 'leader-ready' });

            while (pendingFollowerMessages.length > 0) {
                const req = pendingFollowerMessages.shift();
                handleMessageCore(req, (resp) => postMessage(resp));
            }
            console.debug("aquireLock: Finished processing pending follower messages.");
        } catch (err) {
            console.error(err);
        }

        return new Promise(() => {
            console.debug("aquireLock: Holding lock indefinitely until this worker is terminated or crashes.");
        }); // Hold lock
    });

    isLeader = false;
    leaderReady = false;
    setTimeout(acquireLock, 100);
}

sqlite3InitModule().then(instance => {
    sqlite3 = instance;
    acquireLock();
    while (messageQueue.length > 0) {
        handleMessage(messageQueue.shift());
    }
});