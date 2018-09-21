const readline = require('readline');
const fs = require('fs');
const execSync = require('child_process').execSync;

const outputdir = '/media/imran/Data_2/iauidf/dep94/';
const blockfile = 'idblocks94.csv';
const host = 'localhost';
const user = 'imrandb';
const password = 'imrandb';
const db = 'imrandb';
const table = 'test94';
const quer = 'select * from ' + table + ' where idblock = ';
const shapename = 'parcelle';

const rl = readline.createInterface({
    input: fs.createReadStream(blockfile)
});

let start = Date.now();
console.log('deb ', start/1000);
let writeshapes = function(){
    rl.on('line', function (line) {
	//let idb = parseInt(line,10);
	let query = '"' + quer + line + '"';
	let shapefile = outputdir + line + '/' + shapename;
	console.log('******************************** ');
	console.log('**** ' + outputdir + line);
	fs.mkdirSync(outputdir + line);
	let pgcommand = 'pgsql2shp -f '+ shapefile + ' -h ' + host + ' -u ' + user + ' -P ' + password + ' ' + db + ' ' + query ;
	console.log('**** ' + pgcommand);
	console.log('******************************** ');
	let exec = execSync(pgcommand);
	//console.log(exec.toString());
	//console.log(command);
    })
};

writeshapes();
rl.on('close', () =>{
    let end = Date.now();
    console.log( (end-start) / 1000 + 's elapsed');
});
