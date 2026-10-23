import http from 'node:http';
import nodemailer from 'nodemailer';
import crypto from 'node:crypto';

const required=['SMTP_HOST','SMTP_USER','SMTP_PASSWORD','FEEDBACK_TO','FEEDBACK_FROM'];
const missing=required.filter(k=>!process.env[k]);
if(missing.length){console.error('Missing environment variables:',missing.join(', '));process.exit(1)}
const transport=nodemailer.createTransport({host:process.env.SMTP_HOST,port:Number(process.env.SMTP_PORT||465),secure:Number(process.env.SMTP_PORT||465)===465,auth:{user:process.env.SMTP_USER,pass:process.env.SMTP_PASSWORD}});
const buckets=new Map();
function respond(res,code,data){res.writeHead(code,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});res.end(JSON.stringify(data))}
const server=http.createServer(async(req,res)=>{
 if(req.url==='/health' && req.method==='GET')return respond(res,200,{ok:true});
 if(req.url!=='/api/feedback'||req.method!=='POST')return respond(res,404,{error:'Not found'});
 const ip=(req.socket.remoteAddress||'unknown');const now=Date.now();
 const old=buckets.get(ip)||[];const times=old.filter(t=>now-t<3600_000);
 if(times.length>=5)return respond(res,429,{error:'Too many submissions'});
 times.push(now);buckets.set(ip,times);if(buckets.size>10000)buckets.clear();
 let data='';
 try{
   for await(const part of req){data+=part.toString('utf8');if(Buffer.byteLength(data)>15_000_000)throw new Error('Too large')}
   const x=JSON.parse(data);
   const category=String(x.category||'').slice(0,40),title=String(x.title||'').trim().slice(0,120),message=String(x.message||'').trim().slice(0,4000);
   const replyTo=String(x.replyTo||'').trim();const device=String(x.device||'').slice(0,180);
   if(!title||message.length<10||!['Bug Report','Feature Request','Query','Feedback','Other'].includes(category))return respond(res,400,{error:'Invalid form'});
   if(replyTo && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(replyTo))return respond(res,400,{error:'Invalid reply email'});
   const screenshots=Array.isArray(x.screenshots)?x.screenshots:[];if(screenshots.length>5)return respond(res,400,{error:'Too many attachments'});
   const att=screenshots.map((item,i)=>{
     const type=String(item.contentType||'');if(!['image/jpeg','image/png','image/webp'].includes(type))throw new Error('Invalid image MIME type');
     const base64=String(item.base64||'');const content=Buffer.from(base64,'base64');if(content.length>2_000_000||!content.length)throw new Error('Invalid attachment');
     const sigJpg=content[0]===255&&content[1]===216;const sigPng=content.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]));const sigWebp=content.toString('ascii',0,4)==='RIFF'&&content.toString('ascii',8,12)==='WEBP';
     if(!(type==='image/jpeg'&&sigJpg||type==='image/png'&&sigPng||type==='image/webp'&&sigWebp))throw new Error('Invalid image content');
     return {filename:`screenshot_${i+1}.${type==='image/png'?'png':type==='image/webp'?'webp':'jpg'}`,content,contentType:type};
   });
   const id=crypto.randomUUID();
   await transport.sendMail({from:process.env.FEEDBACK_FROM,to:process.env.FEEDBACK_TO,replyTo:replyTo||undefined,subject:`[Videonic ${category}] ${title}`,text:`Ticket: ${id}\nCategory: ${category}\nTitle: ${title}\nReply: ${replyTo||'Not provided'}\nDevice: ${device}\nApp: ${String(x.appVersion||'').slice(0,50)}\n\n${message}`,attachments:att});
   return respond(res,200,{ok:true,ticket:id});
 }catch(e){console.error('Feedback submit failed:',e.message);return respond(res,e.message==='Too large'?413:400,{error:'Unable to process feedback'})}
});
server.listen(Number(process.env.PORT||3000),'0.0.0.0',()=>console.log('Videonic feedback listening'));
