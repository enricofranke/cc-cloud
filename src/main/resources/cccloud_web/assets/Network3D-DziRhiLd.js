import{n as e,o as t,t as n}from"./jsxRuntime.module-BRbsxUMc.js";import{a as r,c as i,i as a,l as o,n as s,o as c,r as l,s as u,t as d,u as f}from"./Network-pZySyKbu.js";var p={LEFT:0,MIDDLE:1,RIGHT:2,ROTATE:0,DOLLY:1,PAN:2},m={ROTATE:0,PAN:1,DOLLY_PAN:2,DOLLY_ROTATE:3},h=1e3,g=1001,_=1002,v=1003,y=1004,b=1005,x=1006,S=1007,C=1008,w=1009,T=1010,E=1011,D=1012,O=1013,k=1014,A=1015,j=1016,ee=1017,M=1018,te=1020,N=35902,ne=35899,re=1021,ie=1022,ae=1023,oe=1026,P=1027,se=1028,ce=1029,F=1030,le=1031,ue=1033,de=33776,fe=33777,pe=33778,me=33779,he=35840,ge=35841,_e=35842,ve=35843,ye=36196,be=37492,xe=37496,Se=37488,Ce=37489,we=37490,Te=37491,Ee=37808,De=37809,Oe=37810,ke=37811,Ae=37812,je=37813,Me=37814,I=37815,Ne=37816,L=37817,Pe=37818,R=37819,Fe=37820,z=37821,B=36492,Ie=36494,Le=36495,Re=36283,ze=36284,Be=36285,Ve=36286,He=2300,Ue=2301,We=2302,Ge=2303,Ke=2400,qe=2401,Je=2402,Ye=3200,Xe=`srgb`,Ze=`srgb-linear`,Qe=`linear`,$e=`srgb`,et=7680,tt=35044,nt=2e3;function rt(e){for(let t=e.length-1;t>=0;--t)if(e[t]>=65535)return!0;return!1}function it(e){return ArrayBuffer.isView(e)&&!(e instanceof DataView)}function at(e){return document.createElementNS(`http://www.w3.org/1999/xhtml`,e)}function ot(){let e=at(`canvas`);return e.style.display=`block`,e}var st={};function ct(...e){let t=`THREE.`+e.shift();console.log(t,...e)}function lt(e){let t=e[0];if(typeof t==`string`&&t.startsWith(`TSL:`)){let t=e[1];t&&t.isStackTrace?e[0]+=` `+t.getLocation():e[1]=`Stack trace not available. Enable "THREE.Node.captureStackTrace" to capture stack traces.`}return e}function V(...e){e=lt(e);let t=`THREE.`+e.shift();{let n=e[0];n&&n.isStackTrace?console.warn(n.getError(t)):console.warn(t,...e)}}function H(...e){e=lt(e);let t=`THREE.`+e.shift();{let n=e[0];n&&n.isStackTrace?console.error(n.getError(t)):console.error(t,...e)}}function ut(...e){let t=e.join(` `);t in st||(st[t]=!0,V(...e))}function dt(e,t,n){return new Promise(function(r,i){function a(){switch(e.clientWaitSync(t,e.SYNC_FLUSH_COMMANDS_BIT,0)){case e.WAIT_FAILED:i();break;case e.TIMEOUT_EXPIRED:setTimeout(a,n);break;default:r()}}setTimeout(a,n)})}var ft={0:1,2:6,4:7,3:5,1:0,6:2,7:4,5:3},pt=class{addEventListener(e,t){this._listeners===void 0&&(this._listeners={});let n=this._listeners;n[e]===void 0&&(n[e]=[]),n[e].indexOf(t)===-1&&n[e].push(t)}hasEventListener(e,t){let n=this._listeners;return n!==void 0&&n[e]!==void 0&&n[e].indexOf(t)!==-1}removeEventListener(e,t){let n=this._listeners;if(n===void 0)return;let r=n[e];if(r!==void 0){let e=r.indexOf(t);e!==-1&&r.splice(e,1)}}dispatchEvent(e){let t=this._listeners;if(t===void 0)return;let n=t[e.type];if(n!==void 0){e.target=this;let t=n.slice(0);for(let n=0,r=t.length;n<r;n++)t[n].call(this,e);e.target=null}}},mt=`00.01.02.03.04.05.06.07.08.09.0a.0b.0c.0d.0e.0f.10.11.12.13.14.15.16.17.18.19.1a.1b.1c.1d.1e.1f.20.21.22.23.24.25.26.27.28.29.2a.2b.2c.2d.2e.2f.30.31.32.33.34.35.36.37.38.39.3a.3b.3c.3d.3e.3f.40.41.42.43.44.45.46.47.48.49.4a.4b.4c.4d.4e.4f.50.51.52.53.54.55.56.57.58.59.5a.5b.5c.5d.5e.5f.60.61.62.63.64.65.66.67.68.69.6a.6b.6c.6d.6e.6f.70.71.72.73.74.75.76.77.78.79.7a.7b.7c.7d.7e.7f.80.81.82.83.84.85.86.87.88.89.8a.8b.8c.8d.8e.8f.90.91.92.93.94.95.96.97.98.99.9a.9b.9c.9d.9e.9f.a0.a1.a2.a3.a4.a5.a6.a7.a8.a9.aa.ab.ac.ad.ae.af.b0.b1.b2.b3.b4.b5.b6.b7.b8.b9.ba.bb.bc.bd.be.bf.c0.c1.c2.c3.c4.c5.c6.c7.c8.c9.ca.cb.cc.cd.ce.cf.d0.d1.d2.d3.d4.d5.d6.d7.d8.d9.da.db.dc.dd.de.df.e0.e1.e2.e3.e4.e5.e6.e7.e8.e9.ea.eb.ec.ed.ee.ef.f0.f1.f2.f3.f4.f5.f6.f7.f8.f9.fa.fb.fc.fd.fe.ff`.split(`.`),ht=1234567,gt=Math.PI/180,_t=180/Math.PI;function vt(){let e=Math.random()*4294967295|0,t=Math.random()*4294967295|0,n=Math.random()*4294967295|0,r=Math.random()*4294967295|0;return(mt[e&255]+mt[e>>8&255]+mt[e>>16&255]+mt[e>>24&255]+`-`+mt[t&255]+mt[t>>8&255]+`-`+mt[t>>16&15|64]+mt[t>>24&255]+`-`+mt[n&63|128]+mt[n>>8&255]+`-`+mt[n>>16&255]+mt[n>>24&255]+mt[r&255]+mt[r>>8&255]+mt[r>>16&255]+mt[r>>24&255]).toLowerCase()}function U(e,t,n){return Math.max(t,Math.min(n,e))}function yt(e,t){return(e%t+t)%t}function bt(e,t,n,r,i){return r+(e-t)*(i-r)/(n-t)}function xt(e,t,n){return e===t?0:(n-e)/(t-e)}function St(e,t,n){return(1-n)*e+n*t}function Ct(e,t,n,r){return St(e,t,1-Math.exp(-n*r))}function wt(e,t=1){return t-Math.abs(yt(e,t*2)-t)}function Tt(e,t,n){return e<=t?0:e>=n?1:(e=(e-t)/(n-t),e*e*(3-2*e))}function Et(e,t,n){return e<=t?0:e>=n?1:(e=(e-t)/(n-t),e*e*e*(e*(e*6-15)+10))}function Dt(e,t){return e+Math.floor(Math.random()*(t-e+1))}function Ot(e,t){return e+Math.random()*(t-e)}function kt(e){return e*(.5-Math.random())}function At(e){e!==void 0&&(ht=e);let t=ht+=1831565813;return t=Math.imul(t^t>>>15,t|1),t^=t+Math.imul(t^t>>>7,t|61),((t^t>>>14)>>>0)/4294967296}function jt(e){return e*gt}function Mt(e){return e*_t}function Nt(e){return e>0&&Number.isInteger(e)&&2**Math.round(Math.log2(e))===e}function Pt(e){return 2**Math.ceil(Math.log(e)/Math.LN2)}function Ft(e){return 2**Math.floor(Math.log(e)/Math.LN2)}function It(e,t,n,r,i){let a=Math.cos,o=Math.sin,s=a(n/2),c=o(n/2),l=a((t+r)/2),u=o((t+r)/2),d=a((t-r)/2),f=o((t-r)/2),p=a((r-t)/2),m=o((r-t)/2);switch(i){case`XYX`:e.set(s*u,c*d,c*f,s*l);break;case`YZY`:e.set(c*f,s*u,c*d,s*l);break;case`ZXZ`:e.set(c*d,c*f,s*u,s*l);break;case`XZX`:e.set(s*u,c*m,c*p,s*l);break;case`YXY`:e.set(c*p,s*u,c*m,s*l);break;case`ZYZ`:e.set(c*m,c*p,s*u,s*l);break;default:V(`MathUtils: .setQuaternionFromProperEuler() encountered an unknown order: `+i)}}function Lt(e,t){switch(t.constructor){case Float32Array:return e;case Uint32Array:return e/4294967295;case Uint16Array:return e/65535;case Uint8Array:case Uint8ClampedArray:return e/255;case Int32Array:return Math.max(e/2147483647,-1);case Int16Array:return Math.max(e/32767,-1);case Int8Array:return Math.max(e/127,-1);default:throw Error(`THREE.MathUtils: Invalid component type.`)}}function Rt(e,t){switch(t.constructor){case Float32Array:return e;case Uint32Array:return Math.round(e*4294967295);case Uint16Array:return Math.round(e*65535);case Uint8Array:case Uint8ClampedArray:return Math.round(e*255);case Int32Array:return Math.round(e*2147483647);case Int16Array:return Math.round(e*32767);case Int8Array:return Math.round(e*127);default:throw Error(`THREE.MathUtils: Invalid component type.`)}}var zt={DEG2RAD:gt,RAD2DEG:_t,generateUUID:vt,clamp:U,euclideanModulo:yt,mapLinear:bt,inverseLerp:xt,lerp:St,damp:Ct,pingpong:wt,smoothstep:Tt,smootherstep:Et,randInt:Dt,randFloat:Ot,randFloatSpread:kt,seededRandom:At,degToRad:jt,radToDeg:Mt,isPowerOfTwo:Nt,ceilPowerOfTwo:Pt,floorPowerOfTwo:Ft,setQuaternionFromProperEuler:It,normalize:Rt,denormalize:Lt},W=class e{static{e.prototype.isVector2=!0}constructor(e=0,t=0){this.x=e,this.y=t}get width(){return this.x}set width(e){this.x=e}get height(){return this.y}set height(e){this.y=e}set(e,t){return this.x=e,this.y=t,this}setScalar(e){return this.x=e,this.y=e,this}setX(e){return this.x=e,this}setY(e){return this.y=e,this}setComponent(e,t){switch(e){case 0:this.x=t;break;case 1:this.y=t;break;default:throw Error(`THREE.Vector2: index is out of range: `+e)}return this}getComponent(e){switch(e){case 0:return this.x;case 1:return this.y;default:throw Error(`THREE.Vector2: index is out of range: `+e)}}clone(){return new this.constructor(this.x,this.y)}copy(e){return this.x=e.x,this.y=e.y,this}add(e){return this.x+=e.x,this.y+=e.y,this}addScalar(e){return this.x+=e,this.y+=e,this}addVectors(e,t){return this.x=e.x+t.x,this.y=e.y+t.y,this}addScaledVector(e,t){return this.x+=e.x*t,this.y+=e.y*t,this}sub(e){return this.x-=e.x,this.y-=e.y,this}subScalar(e){return this.x-=e,this.y-=e,this}subVectors(e,t){return this.x=e.x-t.x,this.y=e.y-t.y,this}multiply(e){return this.x*=e.x,this.y*=e.y,this}multiplyScalar(e){return this.x*=e,this.y*=e,this}divide(e){return this.x/=e.x,this.y/=e.y,this}divideScalar(e){return this.multiplyScalar(1/e)}applyMatrix3(e){let t=this.x,n=this.y,r=e.elements;return this.x=r[0]*t+r[3]*n+r[6],this.y=r[1]*t+r[4]*n+r[7],this}min(e){return this.x=Math.min(this.x,e.x),this.y=Math.min(this.y,e.y),this}max(e){return this.x=Math.max(this.x,e.x),this.y=Math.max(this.y,e.y),this}clamp(e,t){return this.x=U(this.x,e.x,t.x),this.y=U(this.y,e.y,t.y),this}clampScalar(e,t){return this.x=U(this.x,e,t),this.y=U(this.y,e,t),this}clampLength(e,t){let n=this.length();return this.divideScalar(n||1).multiplyScalar(U(n,e,t))}floor(){return this.x=Math.floor(this.x),this.y=Math.floor(this.y),this}ceil(){return this.x=Math.ceil(this.x),this.y=Math.ceil(this.y),this}round(){return this.x=Math.round(this.x),this.y=Math.round(this.y),this}roundToZero(){return this.x=Math.trunc(this.x),this.y=Math.trunc(this.y),this}negate(){return this.x=-this.x,this.y=-this.y,this}dot(e){return this.x*e.x+this.y*e.y}cross(e){return this.x*e.y-this.y*e.x}lengthSq(){return this.x*this.x+this.y*this.y}length(){return Math.sqrt(this.x*this.x+this.y*this.y)}manhattanLength(){return Math.abs(this.x)+Math.abs(this.y)}normalize(){return this.divideScalar(this.length()||1)}angle(){return Math.atan2(-this.y,-this.x)+Math.PI}angleTo(e){let t=Math.sqrt(this.lengthSq()*e.lengthSq());if(t===0)return Math.PI/2;let n=this.dot(e)/t;return Math.acos(U(n,-1,1))}distanceTo(e){return Math.sqrt(this.distanceToSquared(e))}distanceToSquared(e){let t=this.x-e.x,n=this.y-e.y;return t*t+n*n}manhattanDistanceTo(e){return Math.abs(this.x-e.x)+Math.abs(this.y-e.y)}setLength(e){return this.normalize().multiplyScalar(e)}lerp(e,t){return this.x+=(e.x-this.x)*t,this.y+=(e.y-this.y)*t,this}lerpVectors(e,t,n){return this.x=e.x+(t.x-e.x)*n,this.y=e.y+(t.y-e.y)*n,this}equals(e){return e.x===this.x&&e.y===this.y}fromArray(e,t=0){return this.x=e[t],this.y=e[t+1],this}toArray(e=[],t=0){return e[t]=this.x,e[t+1]=this.y,e}fromBufferAttribute(e,t){return this.x=e.getX(t),this.y=e.getY(t),this}rotateAround(e,t){let n=Math.cos(t),r=Math.sin(t),i=this.x-e.x,a=this.y-e.y;return this.x=i*n-a*r+e.x,this.y=i*r+a*n+e.y,this}random(){return this.x=Math.random(),this.y=Math.random(),this}*[Symbol.iterator](){yield this.x,yield this.y}},Bt=class{constructor(e=0,t=0,n=0,r=1){this.isQuaternion=!0,this._x=e,this._y=t,this._z=n,this._w=r}static slerpFlat(e,t,n,r,i,a,o){let s=n[r+0],c=n[r+1],l=n[r+2],u=n[r+3],d=i[a+0],f=i[a+1],p=i[a+2],m=i[a+3];if(u!==m||s!==d||c!==f||l!==p){let e=s*d+c*f+l*p+u*m;e<0&&(d=-d,f=-f,p=-p,m=-m,e=-e);let t=1-o;if(e<.9995){let n=Math.acos(e),r=Math.sin(n);t=Math.sin(t*n)/r,o=Math.sin(o*n)/r,s=s*t+d*o,c=c*t+f*o,l=l*t+p*o,u=u*t+m*o}else{s=s*t+d*o,c=c*t+f*o,l=l*t+p*o,u=u*t+m*o;let e=1/Math.sqrt(s*s+c*c+l*l+u*u);s*=e,c*=e,l*=e,u*=e}}e[t]=s,e[t+1]=c,e[t+2]=l,e[t+3]=u}static multiplyQuaternionsFlat(e,t,n,r,i,a){let o=n[r],s=n[r+1],c=n[r+2],l=n[r+3],u=i[a],d=i[a+1],f=i[a+2],p=i[a+3];return e[t]=o*p+l*u+s*f-c*d,e[t+1]=s*p+l*d+c*u-o*f,e[t+2]=c*p+l*f+o*d-s*u,e[t+3]=l*p-o*u-s*d-c*f,e}get x(){return this._x}set x(e){this._x=e,this._onChangeCallback()}get y(){return this._y}set y(e){this._y=e,this._onChangeCallback()}get z(){return this._z}set z(e){this._z=e,this._onChangeCallback()}get w(){return this._w}set w(e){this._w=e,this._onChangeCallback()}set(e,t,n,r){return this._x=e,this._y=t,this._z=n,this._w=r,this._onChangeCallback(),this}clone(){return new this.constructor(this._x,this._y,this._z,this._w)}copy(e){return this._x=e.x,this._y=e.y,this._z=e.z,this._w=e.w,this._onChangeCallback(),this}setFromEuler(e,t=!0){let n=e._x,r=e._y,i=e._z,a=e._order,o=Math.cos,s=Math.sin,c=o(n/2),l=o(r/2),u=o(i/2),d=s(n/2),f=s(r/2),p=s(i/2);switch(a){case`XYZ`:this._x=d*l*u+c*f*p,this._y=c*f*u-d*l*p,this._z=c*l*p+d*f*u,this._w=c*l*u-d*f*p;break;case`YXZ`:this._x=d*l*u+c*f*p,this._y=c*f*u-d*l*p,this._z=c*l*p-d*f*u,this._w=c*l*u+d*f*p;break;case`ZXY`:this._x=d*l*u-c*f*p,this._y=c*f*u+d*l*p,this._z=c*l*p+d*f*u,this._w=c*l*u-d*f*p;break;case`ZYX`:this._x=d*l*u-c*f*p,this._y=c*f*u+d*l*p,this._z=c*l*p-d*f*u,this._w=c*l*u+d*f*p;break;case`YZX`:this._x=d*l*u+c*f*p,this._y=c*f*u+d*l*p,this._z=c*l*p-d*f*u,this._w=c*l*u-d*f*p;break;case`XZY`:this._x=d*l*u-c*f*p,this._y=c*f*u-d*l*p,this._z=c*l*p+d*f*u,this._w=c*l*u+d*f*p;break;default:V(`Quaternion: .setFromEuler() encountered an unknown order: `+a)}return t===!0&&this._onChangeCallback(),this}setFromAxisAngle(e,t){let n=t/2,r=Math.sin(n);return this._x=e.x*r,this._y=e.y*r,this._z=e.z*r,this._w=Math.cos(n),this._onChangeCallback(),this}setFromRotationMatrix(e){let t=e.elements,n=t[0],r=t[4],i=t[8],a=t[1],o=t[5],s=t[9],c=t[2],l=t[6],u=t[10],d=n+o+u;if(d>0){let e=.5/Math.sqrt(d+1);this._w=.25/e,this._x=(l-s)*e,this._y=(i-c)*e,this._z=(a-r)*e}else if(n>o&&n>u){let e=2*Math.sqrt(1+n-o-u);this._w=(l-s)/e,this._x=.25*e,this._y=(r+a)/e,this._z=(i+c)/e}else if(o>u){let e=2*Math.sqrt(1+o-n-u);this._w=(i-c)/e,this._x=(r+a)/e,this._y=.25*e,this._z=(s+l)/e}else{let e=2*Math.sqrt(1+u-n-o);this._w=(a-r)/e,this._x=(i+c)/e,this._y=(s+l)/e,this._z=.25*e}return this._onChangeCallback(),this}setFromUnitVectors(e,t){let n=e.dot(t)+1;return n<1e-8?(n=0,Math.abs(e.x)>Math.abs(e.z)?(this._x=-e.y,this._y=e.x,this._z=0,this._w=n):(this._x=0,this._y=-e.z,this._z=e.y,this._w=n)):(this._x=e.y*t.z-e.z*t.y,this._y=e.z*t.x-e.x*t.z,this._z=e.x*t.y-e.y*t.x,this._w=n),this.normalize()}angleTo(e){return 2*Math.acos(Math.abs(U(this.dot(e),-1,1)))}rotateTowards(e,t){let n=this.angleTo(e);if(n===0)return this;let r=Math.min(1,t/n);return this.slerp(e,r),this}identity(){return this.set(0,0,0,1)}invert(){return this.conjugate()}conjugate(){return this._x*=-1,this._y*=-1,this._z*=-1,this._onChangeCallback(),this}dot(e){return this._x*e._x+this._y*e._y+this._z*e._z+this._w*e._w}lengthSq(){return this._x*this._x+this._y*this._y+this._z*this._z+this._w*this._w}length(){return Math.sqrt(this._x*this._x+this._y*this._y+this._z*this._z+this._w*this._w)}normalize(){let e=this.length();return e===0?(this._x=0,this._y=0,this._z=0,this._w=1):(e=1/e,this._x*=e,this._y*=e,this._z*=e,this._w*=e),this._onChangeCallback(),this}multiply(e){return this.multiplyQuaternions(this,e)}premultiply(e){return this.multiplyQuaternions(e,this)}multiplyQuaternions(e,t){let n=e._x,r=e._y,i=e._z,a=e._w,o=t._x,s=t._y,c=t._z,l=t._w;return this._x=n*l+a*o+r*c-i*s,this._y=r*l+a*s+i*o-n*c,this._z=i*l+a*c+n*s-r*o,this._w=a*l-n*o-r*s-i*c,this._onChangeCallback(),this}slerp(e,t){let n=e._x,r=e._y,i=e._z,a=e._w,o=this.dot(e);o<0&&(n=-n,r=-r,i=-i,a=-a,o=-o);let s=1-t;if(o<.9995){let e=Math.acos(o),c=Math.sin(e);s=Math.sin(s*e)/c,t=Math.sin(t*e)/c,this._x=this._x*s+n*t,this._y=this._y*s+r*t,this._z=this._z*s+i*t,this._w=this._w*s+a*t,this._onChangeCallback()}else this._x=this._x*s+n*t,this._y=this._y*s+r*t,this._z=this._z*s+i*t,this._w=this._w*s+a*t,this.normalize();return this}slerpQuaternions(e,t,n){return this.copy(e).slerp(t,n)}random(){let e=2*Math.PI*Math.random(),t=2*Math.PI*Math.random(),n=Math.random(),r=Math.sqrt(1-n),i=Math.sqrt(n);return this.set(r*Math.sin(e),r*Math.cos(e),i*Math.sin(t),i*Math.cos(t))}equals(e){return e._x===this._x&&e._y===this._y&&e._z===this._z&&e._w===this._w}fromArray(e,t=0){return this._x=e[t],this._y=e[t+1],this._z=e[t+2],this._w=e[t+3],this._onChangeCallback(),this}toArray(e=[],t=0){return e[t]=this._x,e[t+1]=this._y,e[t+2]=this._z,e[t+3]=this._w,e}fromBufferAttribute(e,t){return this._x=e.getX(t),this._y=e.getY(t),this._z=e.getZ(t),this._w=e.getW(t),this._onChangeCallback(),this}toJSON(){return this.toArray()}_onChange(e){return this._onChangeCallback=e,this}_onChangeCallback(){}*[Symbol.iterator](){yield this._x,yield this._y,yield this._z,yield this._w}},G=class e{static{e.prototype.isVector3=!0}constructor(e=0,t=0,n=0){this.x=e,this.y=t,this.z=n}set(e,t,n){return n===void 0&&(n=this.z),this.x=e,this.y=t,this.z=n,this}setScalar(e){return this.x=e,this.y=e,this.z=e,this}setX(e){return this.x=e,this}setY(e){return this.y=e,this}setZ(e){return this.z=e,this}setComponent(e,t){switch(e){case 0:this.x=t;break;case 1:this.y=t;break;case 2:this.z=t;break;default:throw Error(`THREE.Vector3: index is out of range: `+e)}return this}getComponent(e){switch(e){case 0:return this.x;case 1:return this.y;case 2:return this.z;default:throw Error(`THREE.Vector3: index is out of range: `+e)}}clone(){return new this.constructor(this.x,this.y,this.z)}copy(e){return this.x=e.x,this.y=e.y,this.z=e.z,this}add(e){return this.x+=e.x,this.y+=e.y,this.z+=e.z,this}addScalar(e){return this.x+=e,this.y+=e,this.z+=e,this}addVectors(e,t){return this.x=e.x+t.x,this.y=e.y+t.y,this.z=e.z+t.z,this}addScaledVector(e,t){return this.x+=e.x*t,this.y+=e.y*t,this.z+=e.z*t,this}sub(e){return this.x-=e.x,this.y-=e.y,this.z-=e.z,this}subScalar(e){return this.x-=e,this.y-=e,this.z-=e,this}subVectors(e,t){return this.x=e.x-t.x,this.y=e.y-t.y,this.z=e.z-t.z,this}multiply(e){return this.x*=e.x,this.y*=e.y,this.z*=e.z,this}multiplyScalar(e){return this.x*=e,this.y*=e,this.z*=e,this}multiplyVectors(e,t){return this.x=e.x*t.x,this.y=e.y*t.y,this.z=e.z*t.z,this}applyEuler(e){return this.applyQuaternion(Ht.setFromEuler(e))}applyAxisAngle(e,t){return this.applyQuaternion(Ht.setFromAxisAngle(e,t))}applyMatrix3(e){let t=this.x,n=this.y,r=this.z,i=e.elements;return this.x=i[0]*t+i[3]*n+i[6]*r,this.y=i[1]*t+i[4]*n+i[7]*r,this.z=i[2]*t+i[5]*n+i[8]*r,this}applyNormalMatrix(e){return this.applyMatrix3(e).normalize()}applyMatrix4(e){let t=this.x,n=this.y,r=this.z,i=e.elements,a=1/(i[3]*t+i[7]*n+i[11]*r+i[15]);return this.x=(i[0]*t+i[4]*n+i[8]*r+i[12])*a,this.y=(i[1]*t+i[5]*n+i[9]*r+i[13])*a,this.z=(i[2]*t+i[6]*n+i[10]*r+i[14])*a,this}applyQuaternion(e){let t=this.x,n=this.y,r=this.z,i=e.x,a=e.y,o=e.z,s=e.w,c=2*(a*r-o*n),l=2*(o*t-i*r),u=2*(i*n-a*t);return this.x=t+s*c+a*u-o*l,this.y=n+s*l+o*c-i*u,this.z=r+s*u+i*l-a*c,this}project(e){return this.applyMatrix4(e.matrixWorldInverse).applyMatrix4(e.projectionMatrix)}unproject(e){return this.applyMatrix4(e.projectionMatrixInverse).applyMatrix4(e.matrixWorld)}transformDirection(e){let t=this.x,n=this.y,r=this.z,i=e.elements;return this.x=i[0]*t+i[4]*n+i[8]*r,this.y=i[1]*t+i[5]*n+i[9]*r,this.z=i[2]*t+i[6]*n+i[10]*r,this.normalize()}divide(e){return this.x/=e.x,this.y/=e.y,this.z/=e.z,this}divideScalar(e){return this.multiplyScalar(1/e)}min(e){return this.x=Math.min(this.x,e.x),this.y=Math.min(this.y,e.y),this.z=Math.min(this.z,e.z),this}max(e){return this.x=Math.max(this.x,e.x),this.y=Math.max(this.y,e.y),this.z=Math.max(this.z,e.z),this}clamp(e,t){return this.x=U(this.x,e.x,t.x),this.y=U(this.y,e.y,t.y),this.z=U(this.z,e.z,t.z),this}clampScalar(e,t){return this.x=U(this.x,e,t),this.y=U(this.y,e,t),this.z=U(this.z,e,t),this}clampLength(e,t){let n=this.length();return this.divideScalar(n||1).multiplyScalar(U(n,e,t))}floor(){return this.x=Math.floor(this.x),this.y=Math.floor(this.y),this.z=Math.floor(this.z),this}ceil(){return this.x=Math.ceil(this.x),this.y=Math.ceil(this.y),this.z=Math.ceil(this.z),this}round(){return this.x=Math.round(this.x),this.y=Math.round(this.y),this.z=Math.round(this.z),this}roundToZero(){return this.x=Math.trunc(this.x),this.y=Math.trunc(this.y),this.z=Math.trunc(this.z),this}negate(){return this.x=-this.x,this.y=-this.y,this.z=-this.z,this}dot(e){return this.x*e.x+this.y*e.y+this.z*e.z}lengthSq(){return this.x*this.x+this.y*this.y+this.z*this.z}length(){return Math.sqrt(this.x*this.x+this.y*this.y+this.z*this.z)}manhattanLength(){return Math.abs(this.x)+Math.abs(this.y)+Math.abs(this.z)}normalize(){return this.divideScalar(this.length()||1)}setLength(e){return this.normalize().multiplyScalar(e)}lerp(e,t){return this.x+=(e.x-this.x)*t,this.y+=(e.y-this.y)*t,this.z+=(e.z-this.z)*t,this}lerpVectors(e,t,n){return this.x=e.x+(t.x-e.x)*n,this.y=e.y+(t.y-e.y)*n,this.z=e.z+(t.z-e.z)*n,this}cross(e){return this.crossVectors(this,e)}crossVectors(e,t){let n=e.x,r=e.y,i=e.z,a=t.x,o=t.y,s=t.z;return this.x=r*s-i*o,this.y=i*a-n*s,this.z=n*o-r*a,this}projectOnVector(e){let t=e.lengthSq();if(t===0)return this.set(0,0,0);let n=e.dot(this)/t;return this.copy(e).multiplyScalar(n)}projectOnPlane(e){return Vt.copy(this).projectOnVector(e),this.sub(Vt)}reflect(e){return this.sub(Vt.copy(e).multiplyScalar(2*this.dot(e)))}angleTo(e){let t=Math.sqrt(this.lengthSq()*e.lengthSq());if(t===0)return Math.PI/2;let n=this.dot(e)/t;return Math.acos(U(n,-1,1))}distanceTo(e){return Math.sqrt(this.distanceToSquared(e))}distanceToSquared(e){let t=this.x-e.x,n=this.y-e.y,r=this.z-e.z;return t*t+n*n+r*r}manhattanDistanceTo(e){return Math.abs(this.x-e.x)+Math.abs(this.y-e.y)+Math.abs(this.z-e.z)}setFromSpherical(e){return this.setFromSphericalCoords(e.radius,e.phi,e.theta)}setFromSphericalCoords(e,t,n){let r=Math.sin(t)*e;return this.x=r*Math.sin(n),this.y=Math.cos(t)*e,this.z=r*Math.cos(n),this}setFromCylindrical(e){return this.setFromCylindricalCoords(e.radius,e.theta,e.y)}setFromCylindricalCoords(e,t,n){return this.x=e*Math.sin(t),this.y=n,this.z=e*Math.cos(t),this}setFromMatrixPosition(e){let t=e.elements;return this.x=t[12],this.y=t[13],this.z=t[14],this}setFromMatrixScale(e){let t=this.setFromMatrixColumn(e,0).length(),n=this.setFromMatrixColumn(e,1).length(),r=this.setFromMatrixColumn(e,2).length();return this.x=t,this.y=n,this.z=r,this}setFromMatrixColumn(e,t){return this.fromArray(e.elements,t*4)}setFromMatrix3Column(e,t){return this.fromArray(e.elements,t*3)}setFromEuler(e){return this.x=e._x,this.y=e._y,this.z=e._z,this}setFromColor(e){return this.x=e.r,this.y=e.g,this.z=e.b,this}equals(e){return e.x===this.x&&e.y===this.y&&e.z===this.z}fromArray(e,t=0){return this.x=e[t],this.y=e[t+1],this.z=e[t+2],this}toArray(e=[],t=0){return e[t]=this.x,e[t+1]=this.y,e[t+2]=this.z,e}fromBufferAttribute(e,t){return this.x=e.getX(t),this.y=e.getY(t),this.z=e.getZ(t),this}random(){return this.x=Math.random(),this.y=Math.random(),this.z=Math.random(),this}randomDirection(){let e=Math.random()*Math.PI*2,t=Math.random()*2-1,n=Math.sqrt(1-t*t);return this.x=n*Math.cos(e),this.y=t,this.z=n*Math.sin(e),this}*[Symbol.iterator](){yield this.x,yield this.y,yield this.z}},Vt=new G,Ht=new Bt,K=class e{static{e.prototype.isMatrix3=!0}constructor(e,t,n,r,i,a,o,s,c){this.elements=[1,0,0,0,1,0,0,0,1],e!==void 0&&this.set(e,t,n,r,i,a,o,s,c)}set(e,t,n,r,i,a,o,s,c){let l=this.elements;return l[0]=e,l[1]=r,l[2]=o,l[3]=t,l[4]=i,l[5]=s,l[6]=n,l[7]=a,l[8]=c,this}identity(){return this.set(1,0,0,0,1,0,0,0,1),this}copy(e){let t=this.elements,n=e.elements;return t[0]=n[0],t[1]=n[1],t[2]=n[2],t[3]=n[3],t[4]=n[4],t[5]=n[5],t[6]=n[6],t[7]=n[7],t[8]=n[8],this}extractBasis(e,t,n){return e.setFromMatrix3Column(this,0),t.setFromMatrix3Column(this,1),n.setFromMatrix3Column(this,2),this}setFromMatrix4(e){let t=e.elements;return this.set(t[0],t[4],t[8],t[1],t[5],t[9],t[2],t[6],t[10]),this}multiply(e){return this.multiplyMatrices(this,e)}premultiply(e){return this.multiplyMatrices(e,this)}multiplyMatrices(e,t){let n=e.elements,r=t.elements,i=this.elements,a=n[0],o=n[3],s=n[6],c=n[1],l=n[4],u=n[7],d=n[2],f=n[5],p=n[8],m=r[0],h=r[3],g=r[6],_=r[1],v=r[4],y=r[7],b=r[2],x=r[5],S=r[8];return i[0]=a*m+o*_+s*b,i[3]=a*h+o*v+s*x,i[6]=a*g+o*y+s*S,i[1]=c*m+l*_+u*b,i[4]=c*h+l*v+u*x,i[7]=c*g+l*y+u*S,i[2]=d*m+f*_+p*b,i[5]=d*h+f*v+p*x,i[8]=d*g+f*y+p*S,this}multiplyScalar(e){let t=this.elements;return t[0]*=e,t[3]*=e,t[6]*=e,t[1]*=e,t[4]*=e,t[7]*=e,t[2]*=e,t[5]*=e,t[8]*=e,this}determinant(){let e=this.elements,t=e[0],n=e[1],r=e[2],i=e[3],a=e[4],o=e[5],s=e[6],c=e[7],l=e[8];return t*a*l-t*o*c-n*i*l+n*o*s+r*i*c-r*a*s}invert(){let e=this.elements,t=e[0],n=e[1],r=e[2],i=e[3],a=e[4],o=e[5],s=e[6],c=e[7],l=e[8],u=l*a-o*c,d=o*s-l*i,f=c*i-a*s,p=t*u+n*d+r*f;if(p===0)return this.set(0,0,0,0,0,0,0,0,0);let m=1/p;return e[0]=u*m,e[1]=(r*c-l*n)*m,e[2]=(o*n-r*a)*m,e[3]=d*m,e[4]=(l*t-r*s)*m,e[5]=(r*i-o*t)*m,e[6]=f*m,e[7]=(n*s-c*t)*m,e[8]=(a*t-n*i)*m,this}transpose(){let e,t=this.elements;return e=t[1],t[1]=t[3],t[3]=e,e=t[2],t[2]=t[6],t[6]=e,e=t[5],t[5]=t[7],t[7]=e,this}getNormalMatrix(e){return this.setFromMatrix4(e).invert().transpose()}transposeIntoArray(e){let t=this.elements;return e[0]=t[0],e[1]=t[3],e[2]=t[6],e[3]=t[1],e[4]=t[4],e[5]=t[7],e[6]=t[2],e[7]=t[5],e[8]=t[8],this}setUvTransform(e,t,n,r,i,a,o){let s=Math.cos(i),c=Math.sin(i);return this.set(n*s,n*c,-n*(s*a+c*o)+a+e,-r*c,r*s,-r*(-c*a+s*o)+o+t,0,0,1),this}scale(e,t){return ut(`Matrix3: .scale() is deprecated. Use .makeScale() instead.`),this.premultiply(Ut.makeScale(e,t)),this}rotate(e){return ut(`Matrix3: .rotate() is deprecated. Use .makeRotation() instead.`),this.premultiply(Ut.makeRotation(-e)),this}translate(e,t){return ut(`Matrix3: .translate() is deprecated. Use .makeTranslation() instead.`),this.premultiply(Ut.makeTranslation(e,t)),this}makeTranslation(e,t){return e.isVector2?this.set(1,0,e.x,0,1,e.y,0,0,1):this.set(1,0,e,0,1,t,0,0,1),this}makeRotation(e){let t=Math.cos(e),n=Math.sin(e);return this.set(t,-n,0,n,t,0,0,0,1),this}makeScale(e,t){return this.set(e,0,0,0,t,0,0,0,1),this}equals(e){let t=this.elements,n=e.elements;for(let e=0;e<9;e++)if(t[e]!==n[e])return!1;return!0}fromArray(e,t=0){for(let n=0;n<9;n++)this.elements[n]=e[n+t];return this}toArray(e=[],t=0){let n=this.elements;return e[t]=n[0],e[t+1]=n[1],e[t+2]=n[2],e[t+3]=n[3],e[t+4]=n[4],e[t+5]=n[5],e[t+6]=n[6],e[t+7]=n[7],e[t+8]=n[8],e}clone(){return new this.constructor().fromArray(this.elements)}},Ut=new K,Wt=new K().set(.4123908,.3575843,.1804808,.212639,.7151687,.0721923,.0193308,.1191948,.9505322),Gt=new K().set(3.2409699,-1.5373832,-.4986108,-.9692436,1.8759675,.0415551,.0556301,-.203977,1.0569715);function Kt(){let e={enabled:!0,workingColorSpace:Ze,spaces:{},convert:function(e,t,n){return this.enabled===!1||t===n||!t||!n||(this.spaces[t].transfer===`srgb`&&(e.r=qt(e.r),e.g=qt(e.g),e.b=qt(e.b)),this.spaces[t].primaries!==this.spaces[n].primaries&&(e.applyMatrix3(this.spaces[t].toXYZ),e.applyMatrix3(this.spaces[n].fromXYZ)),this.spaces[n].transfer===`srgb`&&(e.r=Jt(e.r),e.g=Jt(e.g),e.b=Jt(e.b))),e},workingToColorSpace:function(e,t){return this.convert(e,this.workingColorSpace,t)},colorSpaceToWorking:function(e,t){return this.convert(e,t,this.workingColorSpace)},getPrimaries:function(e){return this.spaces[e].primaries},getTransfer:function(e){return e===``?Qe:this.spaces[e].transfer},getToneMappingMode:function(e){return this.spaces[e].outputColorSpaceConfig.toneMappingMode||`standard`},getLuminanceCoefficients:function(e,t=this.workingColorSpace){return e.fromArray(this.spaces[t].luminanceCoefficients)},define:function(e){Object.assign(this.spaces,e)},_getMatrix:function(e,t,n){return e.copy(this.spaces[t].toXYZ).multiply(this.spaces[n].fromXYZ)},_getDrawingBufferColorSpace:function(e){return this.spaces[e].outputColorSpaceConfig.drawingBufferColorSpace},_getUnpackColorSpace:function(e=this.workingColorSpace){return this.spaces[e].workingColorSpaceConfig.unpackColorSpace},fromWorkingColorSpace:function(t,n){return ut(`ColorManagement: .fromWorkingColorSpace() has been renamed to .workingToColorSpace().`),e.workingToColorSpace(t,n)},toWorkingColorSpace:function(t,n){return ut(`ColorManagement: .toWorkingColorSpace() has been renamed to .colorSpaceToWorking().`),e.colorSpaceToWorking(t,n)}},t=[.64,.33,.3,.6,.15,.06],n=[.2126,.7152,.0722],r=[.3127,.329];return e.define({[Ze]:{primaries:t,whitePoint:r,transfer:Qe,toXYZ:Wt,fromXYZ:Gt,luminanceCoefficients:n,workingColorSpaceConfig:{unpackColorSpace:Xe},outputColorSpaceConfig:{drawingBufferColorSpace:Xe}},[Xe]:{primaries:t,whitePoint:r,transfer:$e,toXYZ:Wt,fromXYZ:Gt,luminanceCoefficients:n,outputColorSpaceConfig:{drawingBufferColorSpace:Xe}}}),e}var q=Kt();function qt(e){return e<.04045?e*.0773993808:(e*.9478672986+.0521327014)**2.4}function Jt(e){return e<.0031308?e*12.92:1.055*e**.41666-.055}var Yt,Xt=class{static getDataURL(e,t=`image/png`){if(/^data:/i.test(e.src)||typeof HTMLCanvasElement>`u`)return e.src;let n;if(e instanceof HTMLCanvasElement)n=e;else{Yt===void 0&&(Yt=at(`canvas`)),Yt.width=e.width,Yt.height=e.height;let t=Yt.getContext(`2d`);e instanceof ImageData?t.putImageData(e,0,0):t.drawImage(e,0,0,e.width,e.height),n=Yt}return n.toDataURL(t)}static sRGBToLinear(e){if(typeof HTMLImageElement<`u`&&e instanceof HTMLImageElement||typeof HTMLCanvasElement<`u`&&e instanceof HTMLCanvasElement||typeof ImageBitmap<`u`&&e instanceof ImageBitmap){let t=at(`canvas`);t.width=e.width,t.height=e.height;let n=t.getContext(`2d`);n.drawImage(e,0,0,e.width,e.height);let r=n.getImageData(0,0,e.width,e.height),i=r.data;for(let e=0;e<i.length;e++)i[e]=qt(i[e]/255)*255;return n.putImageData(r,0,0),t}if(e.data){let t=e.data.slice(0);for(let e=0;e<t.length;e++)t instanceof Uint8Array||t instanceof Uint8ClampedArray?t[e]=Math.floor(qt(t[e]/255)*255):t[e]=qt(t[e]);return{data:t,width:e.width,height:e.height}}return V(`ImageUtils.sRGBToLinear(): Unsupported image type. No color space conversion applied.`),e}},Zt=0,Qt=class{constructor(e=null){this.isTextureSource=!0,Object.defineProperty(this,"id",{value:Zt++}),this.uuid=vt(),this.data=e,this.dataReady=!0,this.version=0}getSize(e){let t=this.data;return typeof HTMLVideoElement<`u`&&t instanceof HTMLVideoElement?e.set(t.videoWidth,t.videoHeight,0):typeof VideoFrame<`u`&&t instanceof VideoFrame?e.set(t.displayWidth,t.displayHeight,0):t===null?e.set(0,0,0):e.set(t.width,t.height,t.depth||0),e}set needsUpdate(e){e===!0&&this.version++}toJSON(e){let t=e===void 0||typeof e==`string`;if(!t&&e.images[this.uuid]!==void 0)return e.images[this.uuid];let n={uuid:this.uuid,url:``},r=this.data;if(r!==null){let e;if(Array.isArray(r)){e=[];for(let t=0,n=r.length;t<n;t++)r[t].isDataTexture?e.push($t(r[t].image)):e.push($t(r[t]))}else e=$t(r);n.url=e}return t||(e.images[this.uuid]=n),n}};function $t(e){return typeof HTMLImageElement<`u`&&e instanceof HTMLImageElement||typeof HTMLCanvasElement<`u`&&e instanceof HTMLCanvasElement||typeof ImageBitmap<`u`&&e instanceof ImageBitmap?Xt.getDataURL(e):e.data?{data:Array.from(e.data),width:e.width,height:e.height,type:e.data.constructor.name}:(V(`Texture: Unable to serialize Texture.`),{})}var en=0,tn=new G,nn=class e extends pt{constructor(t=e.DEFAULT_IMAGE,n=e.DEFAULT_MAPPING,r=g,i=g,a=x,o=C,s=ae,c=w,l=e.DEFAULT_ANISOTROPY,u=``){super(),this.isTexture=!0,Object.defineProperty(this,"id",{value:en++}),this.uuid=vt(),this.name=``,this.source=new Qt(t),this.mipmaps=[],this.mapping=n,this.channel=0,this.wrapS=r,this.wrapT=i,this.magFilter=a,this.minFilter=o,this.anisotropy=l,this.format=s,this.internalFormat=null,this.type=c,this.offset=new W(0,0),this.repeat=new W(1,1),this.center=new W(0,0),this.rotation=0,this.matrixAutoUpdate=!0,this.matrix=new K,this.generateMipmaps=!0,this.premultiplyAlpha=!1,this.flipY=!0,this.unpackAlignment=4,this.colorSpace=u,this.userData={},this.updateRanges=[],this.version=0,this.onUpdate=null,this.renderTarget=null,this.isRenderTargetTexture=!1,this.isArrayTexture=!!(t&&t.depth&&t.depth>1),this.pmremVersion=0,this.normalized=!1}get width(){return this.source.getSize(tn).x}get height(){return this.source.getSize(tn).y}get depth(){return this.source.getSize(tn).z}get image(){return this.source.data}set image(e){this.source.data=e}updateMatrix(){this.matrix.setUvTransform(this.offset.x,this.offset.y,this.repeat.x,this.repeat.y,this.rotation,this.center.x,this.center.y)}addUpdateRange(e,t){this.updateRanges.push({start:e,count:t})}clearUpdateRanges(){this.updateRanges.length=0}clone(){return new this.constructor().copy(this)}copy(e){return this.name=e.name,this.source=e.source,this.mipmaps=e.mipmaps.slice(0),this.mapping=e.mapping,this.channel=e.channel,this.wrapS=e.wrapS,this.wrapT=e.wrapT,this.magFilter=e.magFilter,this.minFilter=e.minFilter,this.anisotropy=e.anisotropy,this.format=e.format,this.internalFormat=e.internalFormat,this.type=e.type,this.normalized=e.normalized,this.offset.copy(e.offset),this.repeat.copy(e.repeat),this.center.copy(e.center),this.rotation=e.rotation,this.matrixAutoUpdate=e.matrixAutoUpdate,this.matrix.copy(e.matrix),this.generateMipmaps=e.generateMipmaps,this.premultiplyAlpha=e.premultiplyAlpha,this.flipY=e.flipY,this.unpackAlignment=e.unpackAlignment,this.colorSpace=e.colorSpace,this.renderTarget=e.renderTarget,this.isRenderTargetTexture=e.isRenderTargetTexture,this.isArrayTexture=e.isArrayTexture,this.userData=JSON.parse(JSON.stringify(e.userData)),this.needsUpdate=!0,this}setValues(e){for(let t in e){let n=e[t];if(n===void 0){V(`Texture.setValues(): parameter '${t}' has value of undefined.`);continue}let r=this[t];r===void 0?V(`Texture.setValues(): property '${t}' does not exist.`):r&&n&&r.isVector2&&n.isVector2||r&&n&&r.isVector3&&n.isVector3||r&&n&&r.isMatrix3&&n.isMatrix3?r.copy(n):this[t]=n}}toJSON(e){let t=e===void 0||typeof e==`string`;if(!t&&e.textures[this.uuid]!==void 0)return e.textures[this.uuid];let n={metadata:{version:4.7,type:`Texture`,generator:`Texture.toJSON`},uuid:this.uuid,name:this.name,image:this.source.toJSON(e).uuid,mapping:this.mapping,channel:this.channel,repeat:[this.repeat.x,this.repeat.y],offset:[this.offset.x,this.offset.y],center:[this.center.x,this.center.y],rotation:this.rotation,wrap:[this.wrapS,this.wrapT],format:this.format,internalFormat:this.internalFormat,type:this.type,normalized:this.normalized,colorSpace:this.colorSpace,minFilter:this.minFilter,magFilter:this.magFilter,anisotropy:this.anisotropy,flipY:this.flipY,generateMipmaps:this.generateMipmaps,premultiplyAlpha:this.premultiplyAlpha,unpackAlignment:this.unpackAlignment};return Object.keys(this.userData).length>0&&(n.userData=this.userData),t||(e.textures[this.uuid]=n),n}dispose(){this.dispatchEvent({type:`dispose`})}transformUv(e){if(this.mapping!==300)return e;if(e.applyMatrix3(this.matrix),e.x<0||e.x>1)switch(this.wrapS){case h:e.x-=Math.floor(e.x);break;case g:e.x=e.x<0?0:1;break;case _:Math.abs(Math.floor(e.x)%2)===1?e.x=Math.ceil(e.x)-e.x:e.x-=Math.floor(e.x)}if(e.y<0||e.y>1)switch(this.wrapT){case h:e.y-=Math.floor(e.y);break;case g:e.y=e.y<0?0:1;break;case _:Math.abs(Math.floor(e.y)%2)===1?e.y=Math.ceil(e.y)-e.y:e.y-=Math.floor(e.y)}return this.flipY&&(e.y=1-e.y),e}set needsUpdate(e){e===!0&&(this.version++,this.source.needsUpdate=!0)}set needsPMREMUpdate(e){e===!0&&this.pmremVersion++}};nn.DEFAULT_IMAGE=null,nn.DEFAULT_MAPPING=300,nn.DEFAULT_ANISOTROPY=1;var rn=class e{static{e.prototype.isVector4=!0}constructor(e=0,t=0,n=0,r=1){this.x=e,this.y=t,this.z=n,this.w=r}get width(){return this.z}set width(e){this.z=e}get height(){return this.w}set height(e){this.w=e}set(e,t,n,r){return this.x=e,this.y=t,this.z=n,this.w=r,this}setScalar(e){return this.x=e,this.y=e,this.z=e,this.w=e,this}setX(e){return this.x=e,this}setY(e){return this.y=e,this}setZ(e){return this.z=e,this}setW(e){return this.w=e,this}setComponent(e,t){switch(e){case 0:this.x=t;break;case 1:this.y=t;break;case 2:this.z=t;break;case 3:this.w=t;break;default:throw Error(`THREE.Vector4: index is out of range: `+e)}return this}getComponent(e){switch(e){case 0:return this.x;case 1:return this.y;case 2:return this.z;case 3:return this.w;default:throw Error(`THREE.Vector4: index is out of range: `+e)}}clone(){return new this.constructor(this.x,this.y,this.z,this.w)}copy(e){return this.x=e.x,this.y=e.y,this.z=e.z,this.w=e.w===void 0?1:e.w,this}add(e){return this.x+=e.x,this.y+=e.y,this.z+=e.z,this.w+=e.w,this}addScalar(e){return this.x+=e,this.y+=e,this.z+=e,this.w+=e,this}addVectors(e,t){return this.x=e.x+t.x,this.y=e.y+t.y,this.z=e.z+t.z,this.w=e.w+t.w,this}addScaledVector(e,t){return this.x+=e.x*t,this.y+=e.y*t,this.z+=e.z*t,this.w+=e.w*t,this}sub(e){return this.x-=e.x,this.y-=e.y,this.z-=e.z,this.w-=e.w,this}subScalar(e){return this.x-=e,this.y-=e,this.z-=e,this.w-=e,this}subVectors(e,t){return this.x=e.x-t.x,this.y=e.y-t.y,this.z=e.z-t.z,this.w=e.w-t.w,this}multiply(e){return this.x*=e.x,this.y*=e.y,this.z*=e.z,this.w*=e.w,this}multiplyScalar(e){return this.x*=e,this.y*=e,this.z*=e,this.w*=e,this}applyMatrix4(e){let t=this.x,n=this.y,r=this.z,i=this.w,a=e.elements;return this.x=a[0]*t+a[4]*n+a[8]*r+a[12]*i,this.y=a[1]*t+a[5]*n+a[9]*r+a[13]*i,this.z=a[2]*t+a[6]*n+a[10]*r+a[14]*i,this.w=a[3]*t+a[7]*n+a[11]*r+a[15]*i,this}divide(e){return this.x/=e.x,this.y/=e.y,this.z/=e.z,this.w/=e.w,this}divideScalar(e){return this.multiplyScalar(1/e)}setAxisAngleFromQuaternion(e){this.w=2*Math.acos(e.w);let t=Math.sqrt(1-e.w*e.w);return t<1e-4?(this.x=1,this.y=0,this.z=0):(this.x=e.x/t,this.y=e.y/t,this.z=e.z/t),this}setAxisAngleFromRotationMatrix(e){let t,n,r,i,a=.01,o=.1,s=e.elements,c=s[0],l=s[4],u=s[8],d=s[1],f=s[5],p=s[9],m=s[2],h=s[6],g=s[10];if(Math.abs(l-d)<a&&Math.abs(u-m)<a&&Math.abs(p-h)<a){if(Math.abs(l+d)<o&&Math.abs(u+m)<o&&Math.abs(p+h)<o&&Math.abs(c+f+g-3)<o)return this.set(1,0,0,0),this;t=Math.PI;let e=(c+1)/2,s=(f+1)/2,_=(g+1)/2,v=(l+d)/4,y=(u+m)/4,b=(p+h)/4;return e>s&&e>_?e<a?(n=0,r=.707106781,i=.707106781):(n=Math.sqrt(e),r=v/n,i=y/n):s>_?s<a?(n=.707106781,r=0,i=.707106781):(r=Math.sqrt(s),n=v/r,i=b/r):_<a?(n=.707106781,r=.707106781,i=0):(i=Math.sqrt(_),n=y/i,r=b/i),this.set(n,r,i,t),this}let _=Math.sqrt((h-p)*(h-p)+(u-m)*(u-m)+(d-l)*(d-l));return Math.abs(_)<.001&&(_=1),this.x=(h-p)/_,this.y=(u-m)/_,this.z=(d-l)/_,this.w=Math.acos((c+f+g-1)/2),this}setFromMatrixPosition(e){let t=e.elements;return this.x=t[12],this.y=t[13],this.z=t[14],this.w=t[15],this}min(e){return this.x=Math.min(this.x,e.x),this.y=Math.min(this.y,e.y),this.z=Math.min(this.z,e.z),this.w=Math.min(this.w,e.w),this}max(e){return this.x=Math.max(this.x,e.x),this.y=Math.max(this.y,e.y),this.z=Math.max(this.z,e.z),this.w=Math.max(this.w,e.w),this}clamp(e,t){return this.x=U(this.x,e.x,t.x),this.y=U(this.y,e.y,t.y),this.z=U(this.z,e.z,t.z),this.w=U(this.w,e.w,t.w),this}clampScalar(e,t){return this.x=U(this.x,e,t),this.y=U(this.y,e,t),this.z=U(this.z,e,t),this.w=U(this.w,e,t),this}clampLength(e,t){let n=this.length();return this.divideScalar(n||1).multiplyScalar(U(n,e,t))}floor(){return this.x=Math.floor(this.x),this.y=Math.floor(this.y),this.z=Math.floor(this.z),this.w=Math.floor(this.w),this}ceil(){return this.x=Math.ceil(this.x),this.y=Math.ceil(this.y),this.z=Math.ceil(this.z),this.w=Math.ceil(this.w),this}round(){return this.x=Math.round(this.x),this.y=Math.round(this.y),this.z=Math.round(this.z),this.w=Math.round(this.w),this}roundToZero(){return this.x=Math.trunc(this.x),this.y=Math.trunc(this.y),this.z=Math.trunc(this.z),this.w=Math.trunc(this.w),this}negate(){return this.x=-this.x,this.y=-this.y,this.z=-this.z,this.w=-this.w,this}dot(e){return this.x*e.x+this.y*e.y+this.z*e.z+this.w*e.w}lengthSq(){return this.x*this.x+this.y*this.y+this.z*this.z+this.w*this.w}length(){return Math.sqrt(this.x*this.x+this.y*this.y+this.z*this.z+this.w*this.w)}manhattanLength(){return Math.abs(this.x)+Math.abs(this.y)+Math.abs(this.z)+Math.abs(this.w)}normalize(){return this.divideScalar(this.length()||1)}setLength(e){return this.normalize().multiplyScalar(e)}lerp(e,t){return this.x+=(e.x-this.x)*t,this.y+=(e.y-this.y)*t,this.z+=(e.z-this.z)*t,this.w+=(e.w-this.w)*t,this}lerpVectors(e,t,n){return this.x=e.x+(t.x-e.x)*n,this.y=e.y+(t.y-e.y)*n,this.z=e.z+(t.z-e.z)*n,this.w=e.w+(t.w-e.w)*n,this}equals(e){return e.x===this.x&&e.y===this.y&&e.z===this.z&&e.w===this.w}fromArray(e,t=0){return this.x=e[t],this.y=e[t+1],this.z=e[t+2],this.w=e[t+3],this}toArray(e=[],t=0){return e[t]=this.x,e[t+1]=this.y,e[t+2]=this.z,e[t+3]=this.w,e}fromBufferAttribute(e,t){return this.x=e.getX(t),this.y=e.getY(t),this.z=e.getZ(t),this.w=e.getW(t),this}random(){return this.x=Math.random(),this.y=Math.random(),this.z=Math.random(),this.w=Math.random(),this}*[Symbol.iterator](){yield this.x,yield this.y,yield this.z,yield this.w}},an=class extends pt{constructor(e=1,t=1,n={}){super(),n=Object.assign({generateMipmaps:!1,internalFormat:null,minFilter:x,depthBuffer:!0,stencilBuffer:!1,resolveColorBuffer:!0,resolveDepthBuffer:!0,resolveStencilBuffer:!0,storeMultisampledColorBuffer:!0,storeMultisampledDepthBuffer:!0,storeMultisampledStencilBuffer:!0,depthTexture:null,samples:0,count:1,depth:1,multiview:!1,useArrayDepthTexture:!1},n),this.isRenderTarget=!0,this.width=e,this.height=t,this.depth=n.depth,this.scissor=new rn(0,0,e,t),this.scissorTest=!1,this.viewport=new rn(0,0,e,t),this.textures=[];let r=new nn({width:e,height:t,depth:n.depth}),i=n.count;for(let e=0;e<i;e++)this.textures[e]=r.clone(),this.textures[e].isRenderTargetTexture=!0,this.textures[e].renderTarget=this;this._setTextureOptions(n),this.depthBuffer=n.depthBuffer,this.stencilBuffer=n.stencilBuffer,this.resolveColorBuffer=n.resolveColorBuffer,this.resolveDepthBuffer=n.resolveDepthBuffer,this.resolveStencilBuffer=n.resolveStencilBuffer,this.storeMultisampledColorBuffer=n.storeMultisampledColorBuffer,this.storeMultisampledDepthBuffer=n.storeMultisampledDepthBuffer,this.storeMultisampledStencilBuffer=n.storeMultisampledStencilBuffer,this._depthTexture=null,this.depthTexture=n.depthTexture,this.samples=n.samples,this.multiview=n.multiview,this.useArrayDepthTexture=n.useArrayDepthTexture}_setTextureOptions(e={}){let t={minFilter:x,generateMipmaps:!1,flipY:!1,internalFormat:null};e.mapping!==void 0&&(t.mapping=e.mapping),e.wrapS!==void 0&&(t.wrapS=e.wrapS),e.wrapT!==void 0&&(t.wrapT=e.wrapT),e.wrapR!==void 0&&(t.wrapR=e.wrapR),e.magFilter!==void 0&&(t.magFilter=e.magFilter),e.minFilter!==void 0&&(t.minFilter=e.minFilter),e.format!==void 0&&(t.format=e.format),e.type!==void 0&&(t.type=e.type),e.anisotropy!==void 0&&(t.anisotropy=e.anisotropy),e.colorSpace!==void 0&&(t.colorSpace=e.colorSpace),e.flipY!==void 0&&(t.flipY=e.flipY),e.generateMipmaps!==void 0&&(t.generateMipmaps=e.generateMipmaps),e.internalFormat!==void 0&&(t.internalFormat=e.internalFormat);for(let e=0;e<this.textures.length;e++)this.textures[e].setValues(t)}get texture(){return this.textures[0]}set texture(e){this.textures[0]=e}set depthTexture(e){this._depthTexture!==null&&this._depthTexture.renderTarget===this&&(this._depthTexture.renderTarget=null),e!==null&&e.renderTarget===null&&(e.renderTarget=this),this._depthTexture=e}get depthTexture(){return this._depthTexture}setSize(e,t,n=1){if(this.width!==e||this.height!==t||this.depth!==n){this.width=e,this.height=t,this.depth=n;for(let r=0,i=this.textures.length;r<i;r++)this.textures[r].image.width=e,this.textures[r].image.height=t,this.textures[r].image.depth=n,this.textures[r].isData3DTexture!==!0&&(this.textures[r].isArrayTexture=this.textures[r].image.depth>1);this.dispose()}this.viewport.set(0,0,e,t),this.scissor.set(0,0,e,t)}clone(){return new this.constructor().copy(this)}copy(e){this.width=e.width,this.height=e.height,this.depth=e.depth,this.scissor.copy(e.scissor),this.scissorTest=e.scissorTest,this.viewport.copy(e.viewport),this.textures.length=0;for(let t=0,n=e.textures.length;t<n;t++){this.textures[t]=e.textures[t].clone(),this.textures[t].isRenderTargetTexture=!0,this.textures[t].renderTarget=this;let n=Object.assign({},e.textures[t].image);this.textures[t].source=new Qt(n)}if(this.depthBuffer=e.depthBuffer,this.stencilBuffer=e.stencilBuffer,this.resolveColorBuffer=e.resolveColorBuffer,this.resolveDepthBuffer=e.resolveDepthBuffer,this.resolveStencilBuffer=e.resolveStencilBuffer,this.storeMultisampledColorBuffer=e.storeMultisampledColorBuffer,this.storeMultisampledDepthBuffer=e.storeMultisampledDepthBuffer,this.storeMultisampledStencilBuffer=e.storeMultisampledStencilBuffer,e.depthTexture!==null){if(e.depthTexture.renderTarget===e){let t=e.depthTexture.clone();t.renderTarget=null,this.depthTexture=t}else this.depthTexture=e.depthTexture}return this.samples=e.samples,this.multiview=e.multiview,this.useArrayDepthTexture=e.useArrayDepthTexture,this}dispose(){this.dispatchEvent({type:`dispose`})}},on=class extends an{constructor(e=1,t=1,n={}){super(e,t,n),this.isWebGLRenderTarget=!0}},sn=class extends nn{constructor(e=null,t=1,n=1,r=1){super(null),this.isDataArrayTexture=!0,this.image={data:e,width:t,height:n,depth:r},this.magFilter=v,this.minFilter=v,this.wrapR=g,this.generateMipmaps=!1,this.flipY=!1,this.unpackAlignment=1,this.layerUpdates=new Set}copy(e){return super.copy(e),this.wrapR=e.wrapR,this}addLayerUpdate(e){this.layerUpdates.add(e)}clearLayerUpdates(){this.layerUpdates.clear()}},cn=class extends nn{constructor(e=null,t=1,n=1,r=1){super(null),this.isData3DTexture=!0,this.image={data:e,width:t,height:n,depth:r},this.magFilter=v,this.minFilter=v,this.wrapR=g,this.generateMipmaps=!1,this.flipY=!1,this.unpackAlignment=1}copy(e){return super.copy(e),this.wrapR=e.wrapR,this}},J=class e{static{e.prototype.isMatrix4=!0}constructor(e,t,n,r,i,a,o,s,c,l,u,d,f,p,m,h){this.elements=[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1],e!==void 0&&this.set(e,t,n,r,i,a,o,s,c,l,u,d,f,p,m,h)}set(e,t,n,r,i,a,o,s,c,l,u,d,f,p,m,h){let g=this.elements;return g[0]=e,g[4]=t,g[8]=n,g[12]=r,g[1]=i,g[5]=a,g[9]=o,g[13]=s,g[2]=c,g[6]=l,g[10]=u,g[14]=d,g[3]=f,g[7]=p,g[11]=m,g[15]=h,this}identity(){return this.set(1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1),this}clone(){return new e().fromArray(this.elements)}copy(e){let t=this.elements,n=e.elements;return t[0]=n[0],t[1]=n[1],t[2]=n[2],t[3]=n[3],t[4]=n[4],t[5]=n[5],t[6]=n[6],t[7]=n[7],t[8]=n[8],t[9]=n[9],t[10]=n[10],t[11]=n[11],t[12]=n[12],t[13]=n[13],t[14]=n[14],t[15]=n[15],this}copyPosition(e){let t=this.elements,n=e.elements;return t[12]=n[12],t[13]=n[13],t[14]=n[14],this}setFromMatrix3(e){let t=e.elements;return this.set(t[0],t[3],t[6],0,t[1],t[4],t[7],0,t[2],t[5],t[8],0,0,0,0,1),this}extractBasis(e,t,n){return this.determinantAffine()===0?(e.set(1,0,0),t.set(0,1,0),n.set(0,0,1),this):(e.setFromMatrixColumn(this,0),t.setFromMatrixColumn(this,1),n.setFromMatrixColumn(this,2),this)}makeBasis(e,t,n){return this.set(e.x,t.x,n.x,0,e.y,t.y,n.y,0,e.z,t.z,n.z,0,0,0,0,1),this}extractRotation(e){if(e.determinantAffine()===0)return this.identity();let t=this.elements,n=e.elements,r=1/ln.setFromMatrixColumn(e,0).length(),i=1/ln.setFromMatrixColumn(e,1).length(),a=1/ln.setFromMatrixColumn(e,2).length();return t[0]=n[0]*r,t[1]=n[1]*r,t[2]=n[2]*r,t[3]=0,t[4]=n[4]*i,t[5]=n[5]*i,t[6]=n[6]*i,t[7]=0,t[8]=n[8]*a,t[9]=n[9]*a,t[10]=n[10]*a,t[11]=0,t[12]=0,t[13]=0,t[14]=0,t[15]=1,this}makeRotationFromEuler(e){let t=this.elements,n=e.x,r=e.y,i=e.z,a=Math.cos(n),o=Math.sin(n),s=Math.cos(r),c=Math.sin(r),l=Math.cos(i),u=Math.sin(i);if(e.order===`XYZ`){let e=a*l,n=a*u,r=o*l,i=o*u;t[0]=s*l,t[4]=-s*u,t[8]=c,t[1]=n+r*c,t[5]=e-i*c,t[9]=-o*s,t[2]=i-e*c,t[6]=r+n*c,t[10]=a*s}else if(e.order===`YXZ`){let e=s*l,n=s*u,r=c*l,i=c*u;t[0]=e+i*o,t[4]=r*o-n,t[8]=a*c,t[1]=a*u,t[5]=a*l,t[9]=-o,t[2]=n*o-r,t[6]=i+e*o,t[10]=a*s}else if(e.order===`ZXY`){let e=s*l,n=s*u,r=c*l,i=c*u;t[0]=e-i*o,t[4]=-a*u,t[8]=r+n*o,t[1]=n+r*o,t[5]=a*l,t[9]=i-e*o,t[2]=-a*c,t[6]=o,t[10]=a*s}else if(e.order===`ZYX`){let e=a*l,n=a*u,r=o*l,i=o*u;t[0]=s*l,t[4]=r*c-n,t[8]=e*c+i,t[1]=s*u,t[5]=i*c+e,t[9]=n*c-r,t[2]=-c,t[6]=o*s,t[10]=a*s}else if(e.order===`YZX`){let e=a*s,n=a*c,r=o*s,i=o*c;t[0]=s*l,t[4]=i-e*u,t[8]=r*u+n,t[1]=u,t[5]=a*l,t[9]=-o*l,t[2]=-c*l,t[6]=n*u+r,t[10]=e-i*u}else if(e.order===`XZY`){let e=a*s,n=a*c,r=o*s,i=o*c;t[0]=s*l,t[4]=-u,t[8]=c*l,t[1]=e*u+i,t[5]=a*l,t[9]=n*u-r,t[2]=r*u-n,t[6]=o*l,t[10]=i*u+e}return t[3]=0,t[7]=0,t[11]=0,t[12]=0,t[13]=0,t[14]=0,t[15]=1,this}makeRotationFromQuaternion(e){return this.compose(dn,e,fn)}lookAt(e,t,n){let r=this.elements;return hn.subVectors(e,t),hn.lengthSq()===0&&(hn.z=1),hn.normalize(),pn.crossVectors(n,hn),pn.lengthSq()===0&&(Math.abs(n.z)===1?hn.x+=1e-4:hn.z+=1e-4,hn.normalize(),pn.crossVectors(n,hn)),pn.normalize(),mn.crossVectors(hn,pn),r[0]=pn.x,r[4]=mn.x,r[8]=hn.x,r[1]=pn.y,r[5]=mn.y,r[9]=hn.y,r[2]=pn.z,r[6]=mn.z,r[10]=hn.z,this}multiply(e){return this.multiplyMatrices(this,e)}premultiply(e){return this.multiplyMatrices(e,this)}multiplyMatrices(e,t){let n=e.elements,r=t.elements,i=this.elements,a=n[0],o=n[4],s=n[8],c=n[12],l=n[1],u=n[5],d=n[9],f=n[13],p=n[2],m=n[6],h=n[10],g=n[14],_=n[3],v=n[7],y=n[11],b=n[15],x=r[0],S=r[4],C=r[8],w=r[12],T=r[1],E=r[5],D=r[9],O=r[13],k=r[2],A=r[6],j=r[10],ee=r[14],M=r[3],te=r[7],N=r[11],ne=r[15];return i[0]=a*x+o*T+s*k+c*M,i[4]=a*S+o*E+s*A+c*te,i[8]=a*C+o*D+s*j+c*N,i[12]=a*w+o*O+s*ee+c*ne,i[1]=l*x+u*T+d*k+f*M,i[5]=l*S+u*E+d*A+f*te,i[9]=l*C+u*D+d*j+f*N,i[13]=l*w+u*O+d*ee+f*ne,i[2]=p*x+m*T+h*k+g*M,i[6]=p*S+m*E+h*A+g*te,i[10]=p*C+m*D+h*j+g*N,i[14]=p*w+m*O+h*ee+g*ne,i[3]=_*x+v*T+y*k+b*M,i[7]=_*S+v*E+y*A+b*te,i[11]=_*C+v*D+y*j+b*N,i[15]=_*w+v*O+y*ee+b*ne,this}multiplyScalar(e){let t=this.elements;return t[0]*=e,t[4]*=e,t[8]*=e,t[12]*=e,t[1]*=e,t[5]*=e,t[9]*=e,t[13]*=e,t[2]*=e,t[6]*=e,t[10]*=e,t[14]*=e,t[3]*=e,t[7]*=e,t[11]*=e,t[15]*=e,this}determinant(){let e=this.elements,t=e[0],n=e[4],r=e[8],i=e[12],a=e[1],o=e[5],s=e[9],c=e[13],l=e[2],u=e[6],d=e[10],f=e[14],p=e[3],m=e[7],h=e[11],g=e[15],_=s*f-c*d,v=o*f-c*u,y=o*d-s*u,b=a*f-c*l,x=a*d-s*l,S=a*u-o*l;return t*(m*_-h*v+g*y)-n*(p*_-h*b+g*x)+r*(p*v-m*b+g*S)-i*(p*y-m*x+h*S)}determinantAffine(){let e=this.elements,t=e[0],n=e[4],r=e[8],i=e[1],a=e[5],o=e[9],s=e[2],c=e[6],l=e[10];return t*(a*l-o*c)-n*(i*l-o*s)+r*(i*c-a*s)}transpose(){let e=this.elements,t;return t=e[1],e[1]=e[4],e[4]=t,t=e[2],e[2]=e[8],e[8]=t,t=e[6],e[6]=e[9],e[9]=t,t=e[3],e[3]=e[12],e[12]=t,t=e[7],e[7]=e[13],e[13]=t,t=e[11],e[11]=e[14],e[14]=t,this}setPosition(e,t,n){let r=this.elements;return e.isVector3?(r[12]=e.x,r[13]=e.y,r[14]=e.z):(r[12]=e,r[13]=t,r[14]=n),this}invert(){let e=this.elements,t=e[0],n=e[1],r=e[2],i=e[3],a=e[4],o=e[5],s=e[6],c=e[7],l=e[8],u=e[9],d=e[10],f=e[11],p=e[12],m=e[13],h=e[14],g=e[15],_=t*o-n*a,v=t*s-r*a,y=t*c-i*a,b=n*s-r*o,x=n*c-i*o,S=r*c-i*s,C=l*m-u*p,w=l*h-d*p,T=l*g-f*p,E=u*h-d*m,D=u*g-f*m,O=d*g-f*h,k=_*O-v*D+y*E+b*T-x*w+S*C;if(k===0)return this.set(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0);let A=1/k;return e[0]=(o*O-s*D+c*E)*A,e[1]=(r*D-n*O-i*E)*A,e[2]=(m*S-h*x+g*b)*A,e[3]=(d*x-u*S-f*b)*A,e[4]=(s*T-a*O-c*w)*A,e[5]=(t*O-r*T+i*w)*A,e[6]=(h*y-p*S-g*v)*A,e[7]=(l*S-d*y+f*v)*A,e[8]=(a*D-o*T+c*C)*A,e[9]=(n*T-t*D-i*C)*A,e[10]=(p*x-m*y+g*_)*A,e[11]=(u*y-l*x-f*_)*A,e[12]=(o*w-a*E-s*C)*A,e[13]=(t*E-n*w+r*C)*A,e[14]=(m*v-p*b-h*_)*A,e[15]=(l*b-u*v+d*_)*A,this}scale(e){let t=this.elements,n=e.x,r=e.y,i=e.z;return t[0]*=n,t[4]*=r,t[8]*=i,t[1]*=n,t[5]*=r,t[9]*=i,t[2]*=n,t[6]*=r,t[10]*=i,t[3]*=n,t[7]*=r,t[11]*=i,this}getMaxScaleOnAxis(){let e=this.elements,t=e[0]*e[0]+e[1]*e[1]+e[2]*e[2],n=e[4]*e[4]+e[5]*e[5]+e[6]*e[6],r=e[8]*e[8]+e[9]*e[9]+e[10]*e[10];return Math.sqrt(Math.max(t,n,r))}makeTranslation(e,t,n){return e.isVector3?this.set(1,0,0,e.x,0,1,0,e.y,0,0,1,e.z,0,0,0,1):this.set(1,0,0,e,0,1,0,t,0,0,1,n,0,0,0,1),this}makeRotationX(e){let t=Math.cos(e),n=Math.sin(e);return this.set(1,0,0,0,0,t,-n,0,0,n,t,0,0,0,0,1),this}makeRotationY(e){let t=Math.cos(e),n=Math.sin(e);return this.set(t,0,n,0,0,1,0,0,-n,0,t,0,0,0,0,1),this}makeRotationZ(e){let t=Math.cos(e),n=Math.sin(e);return this.set(t,-n,0,0,n,t,0,0,0,0,1,0,0,0,0,1),this}makeRotationAxis(e,t){let n=Math.cos(t),r=Math.sin(t),i=1-n,a=e.x,o=e.y,s=e.z,c=i*a,l=i*o;return this.set(c*a+n,c*o-r*s,c*s+r*o,0,c*o+r*s,l*o+n,l*s-r*a,0,c*s-r*o,l*s+r*a,i*s*s+n,0,0,0,0,1),this}makeScale(e,t,n){return this.set(e,0,0,0,0,t,0,0,0,0,n,0,0,0,0,1),this}makeShear(e,t,n,r,i,a){return this.set(1,n,i,0,e,1,a,0,t,r,1,0,0,0,0,1),this}compose(e,t,n){let r=this.elements,i=t._x,a=t._y,o=t._z,s=t._w,c=i+i,l=a+a,u=o+o,d=i*c,f=i*l,p=i*u,m=a*l,h=a*u,g=o*u,_=s*c,v=s*l,y=s*u,b=n.x,x=n.y,S=n.z;return r[0]=(1-(m+g))*b,r[1]=(f+y)*b,r[2]=(p-v)*b,r[3]=0,r[4]=(f-y)*x,r[5]=(1-(d+g))*x,r[6]=(h+_)*x,r[7]=0,r[8]=(p+v)*S,r[9]=(h-_)*S,r[10]=(1-(d+m))*S,r[11]=0,r[12]=e.x,r[13]=e.y,r[14]=e.z,r[15]=1,this}decompose(e,t,n){let r=this.elements;e.x=r[12],e.y=r[13],e.z=r[14];let i=this.determinantAffine();if(i===0)return n.set(1,1,1),t.identity(),this;let a=ln.set(r[0],r[1],r[2]).length(),o=ln.set(r[4],r[5],r[6]).length(),s=ln.set(r[8],r[9],r[10]).length();i<0&&(a=-a),un.copy(this);let c=1/a,l=1/o,u=1/s;return un.elements[0]*=c,un.elements[1]*=c,un.elements[2]*=c,un.elements[4]*=l,un.elements[5]*=l,un.elements[6]*=l,un.elements[8]*=u,un.elements[9]*=u,un.elements[10]*=u,t.setFromRotationMatrix(un),n.x=a,n.y=o,n.z=s,this}makePerspective(e,t,n,r,i,a,o=nt,s=!1){let c=this.elements,l=2*i/(t-e),u=2*i/(n-r),d=(t+e)/(t-e),f=(n+r)/(n-r),p,m;if(s)p=i/(a-i),m=a*i/(a-i);else if(o===2e3)p=-(a+i)/(a-i),m=-2*a*i/(a-i);else if(o===2001)p=-a/(a-i),m=-a*i/(a-i);else throw Error(`THREE.Matrix4.makePerspective(): Invalid coordinate system: `+o);return c[0]=l,c[4]=0,c[8]=d,c[12]=0,c[1]=0,c[5]=u,c[9]=f,c[13]=0,c[2]=0,c[6]=0,c[10]=p,c[14]=m,c[3]=0,c[7]=0,c[11]=-1,c[15]=0,this}makeOrthographic(e,t,n,r,i,a,o=nt,s=!1){let c=this.elements,l=2/(t-e),u=2/(n-r),d=-(t+e)/(t-e),f=-(n+r)/(n-r),p,m;if(s)p=1/(a-i),m=a/(a-i);else if(o===2e3)p=-2/(a-i),m=-(a+i)/(a-i);else if(o===2001)p=-1/(a-i),m=-i/(a-i);else throw Error(`THREE.Matrix4.makeOrthographic(): Invalid coordinate system: `+o);return c[0]=l,c[4]=0,c[8]=0,c[12]=d,c[1]=0,c[5]=u,c[9]=0,c[13]=f,c[2]=0,c[6]=0,c[10]=p,c[14]=m,c[3]=0,c[7]=0,c[11]=0,c[15]=1,this}equals(e){let t=this.elements,n=e.elements;for(let e=0;e<16;e++)if(t[e]!==n[e])return!1;return!0}fromArray(e,t=0){for(let n=0;n<16;n++)this.elements[n]=e[n+t];return this}toArray(e=[],t=0){let n=this.elements;return e[t]=n[0],e[t+1]=n[1],e[t+2]=n[2],e[t+3]=n[3],e[t+4]=n[4],e[t+5]=n[5],e[t+6]=n[6],e[t+7]=n[7],e[t+8]=n[8],e[t+9]=n[9],e[t+10]=n[10],e[t+11]=n[11],e[t+12]=n[12],e[t+13]=n[13],e[t+14]=n[14],e[t+15]=n[15],e}},ln=new G,un=new J,dn=new G(0,0,0),fn=new G(1,1,1),pn=new G,mn=new G,hn=new G,gn=new J,_n=new Bt,vn=class e{constructor(t=0,n=0,r=0,i=e.DEFAULT_ORDER){this.isEuler=!0,this._x=t,this._y=n,this._z=r,this._order=i}get x(){return this._x}set x(e){this._x=e,this._onChangeCallback()}get y(){return this._y}set y(e){this._y=e,this._onChangeCallback()}get z(){return this._z}set z(e){this._z=e,this._onChangeCallback()}get order(){return this._order}set order(e){this._order=e,this._onChangeCallback()}set(e,t,n,r=this._order){return this._x=e,this._y=t,this._z=n,this._order=r,this._onChangeCallback(),this}clone(){return new this.constructor(this._x,this._y,this._z,this._order)}copy(e){return this._x=e._x,this._y=e._y,this._z=e._z,this._order=e._order,this._onChangeCallback(),this}setFromRotationMatrix(e,t=this._order,n=!0){let r=e.elements,i=r[0],a=r[4],o=r[8],s=r[1],c=r[5],l=r[9],u=r[2],d=r[6],f=r[10];switch(t){case`XYZ`:this._y=Math.asin(U(o,-1,1)),Math.abs(o)<.9999999?(this._x=Math.atan2(-l,f),this._z=Math.atan2(-a,i)):(this._x=Math.atan2(d,c),this._z=0);break;case`YXZ`:this._x=Math.asin(-U(l,-1,1)),Math.abs(l)<.9999999?(this._y=Math.atan2(o,f),this._z=Math.atan2(s,c)):(this._y=Math.atan2(-u,i),this._z=0);break;case`ZXY`:this._x=Math.asin(U(d,-1,1)),Math.abs(d)<.9999999?(this._y=Math.atan2(-u,f),this._z=Math.atan2(-a,c)):(this._y=0,this._z=Math.atan2(s,i));break;case`ZYX`:this._y=Math.asin(-U(u,-1,1)),Math.abs(u)<.9999999?(this._x=Math.atan2(d,f),this._z=Math.atan2(s,i)):(this._x=0,this._z=Math.atan2(-a,c));break;case`YZX`:this._z=Math.asin(U(s,-1,1)),Math.abs(s)<.9999999?(this._x=Math.atan2(-l,c),this._y=Math.atan2(-u,i)):(this._x=0,this._y=Math.atan2(o,f));break;case`XZY`:this._z=Math.asin(-U(a,-1,1)),Math.abs(a)<.9999999?(this._x=Math.atan2(d,c),this._y=Math.atan2(o,i)):(this._x=Math.atan2(-l,f),this._y=0);break;default:V(`Euler: .setFromRotationMatrix() encountered an unknown order: `+t)}return this._order=t,n===!0&&this._onChangeCallback(),this}setFromQuaternion(e,t,n){return gn.makeRotationFromQuaternion(e),this.setFromRotationMatrix(gn,t,n)}setFromVector3(e,t=this._order){return this.set(e.x,e.y,e.z,t)}reorder(e){return _n.setFromEuler(this),this.setFromQuaternion(_n,e)}equals(e){return e._x===this._x&&e._y===this._y&&e._z===this._z&&e._order===this._order}fromArray(e){return this._x=e[0],this._y=e[1],this._z=e[2],e[3]!==void 0&&(this._order=e[3]),this._onChangeCallback(),this}toArray(e=[],t=0){return e[t]=this._x,e[t+1]=this._y,e[t+2]=this._z,e[t+3]=this._order,e}_onChange(e){return this._onChangeCallback=e,this}_onChangeCallback(){}*[Symbol.iterator](){yield this._x,yield this._y,yield this._z,yield this._order}};vn.DEFAULT_ORDER=`XYZ`;var yn=class{constructor(){this.mask=1}set(e){this.mask=1<<e>>>0}enable(e){this.mask|=1<<e}enableAll(){this.mask=-1}toggle(e){this.mask^=1<<e}disable(e){this.mask&=~(1<<e)}disableAll(){this.mask=0}test(e){return(this.mask&e.mask)!==0}isEnabled(e){return!!(this.mask&1<<e)}},bn=0,xn=new G,Sn=new Bt,Cn=new J,wn=new G,Tn=new G,En=new G,Dn=new Bt,On=new G(1,0,0),kn=new G(0,1,0),An=new G(0,0,1),jn={type:`added`},Mn={type:`removed`},Nn={type:`childadded`,child:null},Pn={type:`childremoved`,child:null},Fn=class e extends pt{constructor(){super(),this.isObject3D=!0,Object.defineProperty(this,"id",{value:bn++}),this.uuid=vt(),this.name=``,this.type=`Object3D`,this.parent=null,this.children=[],this.up=e.DEFAULT_UP.clone();let t=new G,n=new vn,r=new Bt,i=new G(1,1,1);function a(){r.setFromEuler(n,!1)}function o(){n.setFromQuaternion(r,void 0,!1)}n._onChange(a),r._onChange(o),Object.defineProperties(this,{position:{configurable:!0,enumerable:!0,value:t},rotation:{configurable:!0,enumerable:!0,value:n},quaternion:{configurable:!0,enumerable:!0,value:r},scale:{configurable:!0,enumerable:!0,value:i},modelViewMatrix:{value:new J},normalMatrix:{value:new K}}),this.matrix=new J,this.matrixWorld=new J,this.matrixAutoUpdate=e.DEFAULT_MATRIX_AUTO_UPDATE,this.matrixWorldAutoUpdate=e.DEFAULT_MATRIX_WORLD_AUTO_UPDATE,this.matrixWorldNeedsUpdate=!1,this.layers=new yn,this.visible=!0,this.castShadow=!1,this.receiveShadow=!1,this.frustumCulled=!0,this.renderOrder=0,this.animations=[],this.customDepthMaterial=void 0,this.customDistanceMaterial=void 0,this.static=!1,this.userData={},this.pivot=null}onBeforeShadow(){}onAfterShadow(){}onBeforeRender(){}onAfterRender(){}applyMatrix4(e){this.matrixAutoUpdate&&this.updateMatrix(),this.matrix.premultiply(e),this.matrix.decompose(this.position,this.quaternion,this.scale)}applyQuaternion(e){return this.quaternion.premultiply(e),this}setRotationFromAxisAngle(e,t){this.quaternion.setFromAxisAngle(e,t)}setRotationFromEuler(e){this.quaternion.setFromEuler(e,!0)}setRotationFromMatrix(e){this.quaternion.setFromRotationMatrix(e)}setRotationFromQuaternion(e){this.quaternion.copy(e)}rotateOnAxis(e,t){return Sn.setFromAxisAngle(e,t),this.quaternion.multiply(Sn),this}rotateOnWorldAxis(e,t){return Sn.setFromAxisAngle(e,t),this.quaternion.premultiply(Sn),this}rotateX(e){return this.rotateOnAxis(On,e)}rotateY(e){return this.rotateOnAxis(kn,e)}rotateZ(e){return this.rotateOnAxis(An,e)}translateOnAxis(e,t){return xn.copy(e).applyQuaternion(this.quaternion),this.position.add(xn.multiplyScalar(t)),this}translateX(e){return this.translateOnAxis(On,e)}translateY(e){return this.translateOnAxis(kn,e)}translateZ(e){return this.translateOnAxis(An,e)}localToWorld(e){return this.updateWorldMatrix(!0,!1),e.applyMatrix4(this.matrixWorld)}worldToLocal(e){return this.updateWorldMatrix(!0,!1),e.applyMatrix4(Cn.copy(this.matrixWorld).invert())}lookAt(e,t,n){e.isVector3?wn.copy(e):wn.set(e,t,n);let r=this.parent;this.updateWorldMatrix(!0,!1),Tn.setFromMatrixPosition(this.matrixWorld),this.isCamera||this.isLight?Cn.lookAt(Tn,wn,this.up):Cn.lookAt(wn,Tn,this.up),this.quaternion.setFromRotationMatrix(Cn),r&&(Cn.extractRotation(r.matrixWorld),Sn.setFromRotationMatrix(Cn),this.quaternion.premultiply(Sn.invert()))}add(e){if(arguments.length>1){for(let e=0;e<arguments.length;e++)this.add(arguments[e]);return this}return e===this?(H(`Object3D.add: object can't be added as a child of itself.`,e),this):(e&&e.isObject3D?(e.removeFromParent(),e.parent=this,this.children.push(e),e.dispatchEvent(jn),Nn.child=e,this.dispatchEvent(Nn),Nn.child=null):H(`Object3D.add: object not an instance of THREE.Object3D.`,e),this)}remove(e){if(arguments.length>1){for(let e=0;e<arguments.length;e++)this.remove(arguments[e]);return this}let t=this.children.indexOf(e);return t!==-1&&(e.parent=null,this.children.splice(t,1),e.dispatchEvent(Mn),Pn.child=e,this.dispatchEvent(Pn),Pn.child=null),this}removeFromParent(){let e=this.parent;return e!==null&&e.remove(this),this}clear(){return this.remove(...this.children)}attach(e){return this.updateWorldMatrix(!0,!1),Cn.copy(this.matrixWorld).invert(),e.parent!==null&&(e.parent.updateWorldMatrix(!0,!1),Cn.multiply(e.parent.matrixWorld)),e.applyMatrix4(Cn),e.removeFromParent(),e.parent=this,this.children.push(e),e.updateWorldMatrix(!1,!0),e.dispatchEvent(jn),Nn.child=e,this.dispatchEvent(Nn),Nn.child=null,this}getObjectById(e){return this.getObjectByProperty(`id`,e)}getObjectByName(e){return this.getObjectByProperty(`name`,e)}getObjectByProperty(e,t){if(this[e]===t)return this;for(let n=0,r=this.children.length;n<r;n++){let r=this.children[n].getObjectByProperty(e,t);if(r!==void 0)return r}}getObjectsByProperty(e,t,n=[]){this[e]===t&&n.push(this);let r=this.children;for(let i=0,a=r.length;i<a;i++)r[i].getObjectsByProperty(e,t,n);return n}getWorldPosition(e){return this.updateWorldMatrix(!0,!1),e.setFromMatrixPosition(this.matrixWorld)}getWorldQuaternion(e){return this.updateWorldMatrix(!0,!1),this.matrixWorld.decompose(Tn,e,En),e}getWorldScale(e){return this.updateWorldMatrix(!0,!1),this.matrixWorld.decompose(Tn,Dn,e),e}getWorldDirection(e){this.updateWorldMatrix(!0,!1);let t=this.matrixWorld.elements;return e.set(t[8],t[9],t[10]).normalize()}raycast(){}intersectsFrustum(){}traverse(e){e(this);let t=this.children;for(let n=0,r=t.length;n<r;n++)t[n].traverse(e)}traverseVisible(e){if(this.visible===!1)return;e(this);let t=this.children;for(let n=0,r=t.length;n<r;n++)t[n].traverseVisible(e)}traverseAncestors(e){let t=this.parent;t!==null&&(e(t),t.traverseAncestors(e))}updateMatrix(){this.matrix.compose(this.position,this.quaternion,this.scale);let e=this.pivot;if(e!==null){let t=e.x,n=e.y,r=e.z,i=this.matrix.elements;i[12]+=t-i[0]*t-i[4]*n-i[8]*r,i[13]+=n-i[1]*t-i[5]*n-i[9]*r,i[14]+=r-i[2]*t-i[6]*n-i[10]*r}this.matrixWorldNeedsUpdate=!0}updateMatrixWorld(e){this.matrixAutoUpdate&&this.updateMatrix(),(this.matrixWorldNeedsUpdate||e)&&(this.matrixWorldAutoUpdate===!0&&(this.parent===null?this.matrixWorld.copy(this.matrix):this.matrixWorld.multiplyMatrices(this.parent.matrixWorld,this.matrix)),this.matrixWorldNeedsUpdate=!1,e=!0);let t=this.children;for(let n=0,r=t.length;n<r;n++)t[n].updateMatrixWorld(e)}updateWorldMatrix(e,t,n=!1){let r=this.parent;if(e===!0&&r!==null&&r.updateWorldMatrix(!0,!1),this.matrixAutoUpdate&&this.updateMatrix(),(this.matrixWorldNeedsUpdate||n)&&(this.matrixWorldAutoUpdate===!0&&(this.parent===null?this.matrixWorld.copy(this.matrix):this.matrixWorld.multiplyMatrices(this.parent.matrixWorld,this.matrix)),this.matrixWorldNeedsUpdate=!1,n=!0),t===!0){let e=this.children;for(let t=0,r=e.length;t<r;t++)e[t].updateWorldMatrix(!1,!0,n)}}toJSON(e){let t=e===void 0||typeof e==`string`,n={};t&&(e={geometries:{},materials:{},textures:{},images:{},shapes:{},skeletons:{},animations:{},nodes:{}},n.metadata={version:4.7,type:`Object`,generator:`Object3D.toJSON`});let r={};r.uuid=this.uuid,r.type=this.type,r.name=this.name,r.castShadow=this.castShadow,r.receiveShadow=this.receiveShadow,r.visible=this.visible,r.frustumCulled=this.frustumCulled,r.renderOrder=this.renderOrder,r.static=this.static,r.matrixAutoUpdate=this.matrixAutoUpdate,Object.keys(this.userData).length>0&&(r.userData=this.userData),r.layers=this.layers.mask,r.matrix=this.matrix.toArray(),r.up=this.up.toArray(),this.pivot!==null&&(r.pivot=this.pivot.toArray()),this.morphTargetDictionary!==void 0&&(r.morphTargetDictionary=Object.assign({},this.morphTargetDictionary)),this.morphTargetInfluences!==void 0&&(r.morphTargetInfluences=this.morphTargetInfluences.slice()),this.isInstancedMesh&&(r.type=`InstancedMesh`,r.count=this.count,r.instanceMatrix=this.instanceMatrix.toJSON(),this.instanceColor!==null&&(r.instanceColor=this.instanceColor.toJSON())),this.isBatchedMesh&&(r.type=`BatchedMesh`,r.perObjectFrustumCulled=this.perObjectFrustumCulled,r.sortObjects=this.sortObjects,r.drawRanges=this._drawRanges,r.reservedRanges=this._reservedRanges,r.geometryInfo=this._geometryInfo.map(e=>({...e,boundingBox:e.boundingBox?e.boundingBox.toJSON():void 0,boundingSphere:e.boundingSphere?e.boundingSphere.toJSON():void 0})),r.instanceInfo=this._instanceInfo.map(e=>({...e})),r.availableInstanceIds=this._availableInstanceIds.slice(),r.availableGeometryIds=this._availableGeometryIds.slice(),r.nextIndexStart=this._nextIndexStart,r.nextVertexStart=this._nextVertexStart,r.geometryCount=this._geometryCount,r.maxInstanceCount=this._maxInstanceCount,r.maxVertexCount=this._maxVertexCount,r.maxIndexCount=this._maxIndexCount,r.geometryInitialized=this._geometryInitialized,r.matricesTexture=this._matricesTexture.toJSON(e),r.indirectTexture=this._indirectTexture.toJSON(e),this._colorsTexture!==null&&(r.colorsTexture=this._colorsTexture.toJSON(e)),this.boundingSphere!==null&&(r.boundingSphere=this.boundingSphere.toJSON()),this.boundingBox!==null&&(r.boundingBox=this.boundingBox.toJSON()));function i(t,n){return t[n.uuid]===void 0&&(t[n.uuid]=n.toJSON(e)),n.uuid}if(this.isScene)this.background&&(this.background.isColor?r.background=this.background.toJSON():this.background.isTexture&&(r.background=this.background.toJSON(e).uuid)),this.environment&&this.environment.isTexture&&this.environment.isRenderTargetTexture!==!0&&(r.environment=this.environment.toJSON(e).uuid);else if(this.isMesh||this.isLine||this.isPoints){r.geometry=i(e.geometries,this.geometry);let t=this.geometry.parameters;if(t!==void 0&&t.shapes!==void 0){let n=t.shapes;if(Array.isArray(n))for(let t=0,r=n.length;t<r;t++){let r=n[t];i(e.shapes,r)}else i(e.shapes,n)}}if(this.isSkinnedMesh&&(r.bindMode=this.bindMode,r.bindMatrix=this.bindMatrix.toArray(),this.skeleton!==void 0&&(i(e.skeletons,this.skeleton),r.skeleton=this.skeleton.uuid)),this.material!==void 0){if(Array.isArray(this.material)){let t=[];for(let n=0,r=this.material.length;n<r;n++)t.push(i(e.materials,this.material[n]));r.material=t}else r.material=i(e.materials,this.material)}if(this.children.length>0){r.children=[];for(let t=0;t<this.children.length;t++)r.children.push(this.children[t].toJSON(e).object)}if(this.animations.length>0){r.animations=[];for(let t=0;t<this.animations.length;t++){let n=this.animations[t];r.animations.push(i(e.animations,n))}}if(t){let t=a(e.geometries),r=a(e.materials),i=a(e.textures),o=a(e.images),s=a(e.shapes),c=a(e.skeletons),l=a(e.animations),u=a(e.nodes);t.length>0&&(n.geometries=t),r.length>0&&(n.materials=r),i.length>0&&(n.textures=i),o.length>0&&(n.images=o),s.length>0&&(n.shapes=s),c.length>0&&(n.skeletons=c),l.length>0&&(n.animations=l),u.length>0&&(n.nodes=u)}return n.object=r,n;function a(e){let t=[];for(let n in e){let r=e[n];delete r.metadata,t.push(r)}return t}}clone(e){return new this.constructor().copy(this,e)}copy(e,t=!0){if(this.name=e.name,this.up.copy(e.up),this.position.copy(e.position),this.rotation.order=e.rotation.order,this.quaternion.copy(e.quaternion),this.scale.copy(e.scale),this.pivot=e.pivot===null?null:e.pivot.clone(),this.matrix.copy(e.matrix),this.matrixWorld.copy(e.matrixWorld),this.matrixAutoUpdate=e.matrixAutoUpdate,this.matrixWorldAutoUpdate=e.matrixWorldAutoUpdate,this.matrixWorldNeedsUpdate=e.matrixWorldNeedsUpdate,this.layers.mask=e.layers.mask,this.visible=e.visible,this.castShadow=e.castShadow,this.receiveShadow=e.receiveShadow,this.frustumCulled=e.frustumCulled,this.renderOrder=e.renderOrder,this.static=e.static,this.animations=e.animations.slice(),this.userData=JSON.parse(JSON.stringify(e.userData)),t===!0)for(let t=0;t<e.children.length;t++){let n=e.children[t];this.add(n.clone())}return this}dispose(){this.dispatchEvent({type:`dispose`})}};Fn.DEFAULT_UP=new G(0,1,0),Fn.DEFAULT_MATRIX_AUTO_UPDATE=!0,Fn.DEFAULT_MATRIX_WORLD_AUTO_UPDATE=!0;var In=class extends Fn{constructor(){super(),this.isGroup=!0,this.type=`Group`}},Ln={type:`move`},Rn=class{constructor(){this._targetRay=null,this._grip=null,this._hand=null}getHandSpace(){return this._hand===null&&(this._hand=new In,this._hand.matrixAutoUpdate=!1,this._hand.visible=!1,this._hand.joints={},this._hand.inputState={pinching:!1}),this._hand}getTargetRaySpace(){return this._targetRay===null&&(this._targetRay=new In,this._targetRay.matrixAutoUpdate=!1,this._targetRay.visible=!1,this._targetRay.hasLinearVelocity=!1,this._targetRay.linearVelocity=new G,this._targetRay.hasAngularVelocity=!1,this._targetRay.angularVelocity=new G),this._targetRay}getGripSpace(){return this._grip===null&&(this._grip=new In,this._grip.matrixAutoUpdate=!1,this._grip.visible=!1,this._grip.hasLinearVelocity=!1,this._grip.linearVelocity=new G,this._grip.hasAngularVelocity=!1,this._grip.angularVelocity=new G,this._grip.eventsEnabled=!1),this._grip}dispatchEvent(e){return this._targetRay!==null&&this._targetRay.dispatchEvent(e),this._grip!==null&&this._grip.dispatchEvent(e),this._hand!==null&&this._hand.dispatchEvent(e),this}connect(e){if(e&&e.hand){let t=this._hand;if(t)for(let n of e.hand.values())this._getHandJoint(t,n)}return this.dispatchEvent({type:`connected`,data:e}),this}disconnect(e){return this.dispatchEvent({type:`disconnected`,data:e}),this._targetRay!==null&&(this._targetRay.visible=!1),this._grip!==null&&(this._grip.visible=!1),this._hand!==null&&(this._hand.visible=!1),this}update(e,t,n){let r=null,i=null,a=null,o=this._targetRay,s=this._grip,c=this._hand;if(e&&t.session.visibilityState!==`visible-blurred`){if(c&&e.hand){a=!0;for(let r of e.hand.values()){let e=t.getJointPose(r,n),i=this._getHandJoint(c,r);e!==null&&(i.matrix.fromArray(e.transform.matrix),i.matrix.decompose(i.position,i.rotation,i.scale),i.matrixWorldNeedsUpdate=!0,i.jointRadius=e.radius),i.visible=e!==null}let r=c.joints[`index-finger-tip`],i=c.joints[`thumb-tip`],o=r.position.distanceTo(i.position);c.inputState.pinching&&o>.025?(c.inputState.pinching=!1,this.dispatchEvent({type:`pinchend`,handedness:e.handedness,target:this})):!c.inputState.pinching&&o<=.015&&(c.inputState.pinching=!0,this.dispatchEvent({type:`pinchstart`,handedness:e.handedness,target:this}))}else s!==null&&e.gripSpace&&(i=t.getPose(e.gripSpace,n),i!==null&&(s.matrix.fromArray(i.transform.matrix),s.matrix.decompose(s.position,s.rotation,s.scale),s.matrixWorldNeedsUpdate=!0,i.linearVelocity?(s.hasLinearVelocity=!0,s.linearVelocity.copy(i.linearVelocity)):s.hasLinearVelocity=!1,i.angularVelocity?(s.hasAngularVelocity=!0,s.angularVelocity.copy(i.angularVelocity)):s.hasAngularVelocity=!1,s.eventsEnabled&&s.dispatchEvent({type:`gripUpdated`,data:e,target:this})));o!==null&&(r=t.getPose(e.targetRaySpace,n),r===null&&i!==null&&(r=i),r!==null&&(o.matrix.fromArray(r.transform.matrix),o.matrix.decompose(o.position,o.rotation,o.scale),o.matrixWorldNeedsUpdate=!0,r.linearVelocity?(o.hasLinearVelocity=!0,o.linearVelocity.copy(r.linearVelocity)):o.hasLinearVelocity=!1,r.angularVelocity?(o.hasAngularVelocity=!0,o.angularVelocity.copy(r.angularVelocity)):o.hasAngularVelocity=!1,this.dispatchEvent(Ln)))}return o!==null&&(o.visible=r!==null),s!==null&&(s.visible=i!==null),c!==null&&(c.visible=a!==null),this}_getHandJoint(e,t){if(e.joints[t.jointName]===void 0){let n=new In;n.matrixAutoUpdate=!1,n.visible=!1,e.joints[t.jointName]=n,e.add(n)}return e.joints[t.jointName]}},zn={aliceblue:15792383,antiquewhite:16444375,aqua:65535,aquamarine:8388564,azure:15794175,beige:16119260,bisque:16770244,black:0,blanchedalmond:16772045,blue:255,blueviolet:9055202,brown:10824234,burlywood:14596231,cadetblue:6266528,chartreuse:8388352,chocolate:13789470,coral:16744272,cornflowerblue:6591981,cornsilk:16775388,crimson:14423100,cyan:65535,darkblue:139,darkcyan:35723,darkgoldenrod:12092939,darkgray:11119017,darkgreen:25600,darkgrey:11119017,darkkhaki:12433259,darkmagenta:9109643,darkolivegreen:5597999,darkorange:16747520,darkorchid:10040012,darkred:9109504,darksalmon:15308410,darkseagreen:9419919,darkslateblue:4734347,darkslategray:3100495,darkslategrey:3100495,darkturquoise:52945,darkviolet:9699539,deeppink:16716947,deepskyblue:49151,dimgray:6908265,dimgrey:6908265,dodgerblue:2003199,firebrick:11674146,floralwhite:16775920,forestgreen:2263842,fuchsia:16711935,gainsboro:14474460,ghostwhite:16316671,gold:16766720,goldenrod:14329120,gray:8421504,green:32768,greenyellow:11403055,grey:8421504,honeydew:15794160,hotpink:16738740,indianred:13458524,indigo:4915330,ivory:16777200,khaki:15787660,lavender:15132410,lavenderblush:16773365,lawngreen:8190976,lemonchiffon:16775885,lightblue:11393254,lightcoral:15761536,lightcyan:14745599,lightgoldenrodyellow:16448210,lightgray:13882323,lightgreen:9498256,lightgrey:13882323,lightpink:16758465,lightsalmon:16752762,lightseagreen:2142890,lightskyblue:8900346,lightslategray:7833753,lightslategrey:7833753,lightsteelblue:11584734,lightyellow:16777184,lime:65280,limegreen:3329330,linen:16445670,magenta:16711935,maroon:8388608,mediumaquamarine:6737322,mediumblue:205,mediumorchid:12211667,mediumpurple:9662683,mediumseagreen:3978097,mediumslateblue:8087790,mediumspringgreen:64154,mediumturquoise:4772300,mediumvioletred:13047173,midnightblue:1644912,mintcream:16121850,mistyrose:16770273,moccasin:16770229,navajowhite:16768685,navy:128,oldlace:16643558,olive:8421376,olivedrab:7048739,orange:16753920,orangered:16729344,orchid:14315734,palegoldenrod:15657130,palegreen:10025880,paleturquoise:11529966,palevioletred:14381203,papayawhip:16773077,peachpuff:16767673,peru:13468991,pink:16761035,plum:14524637,powderblue:11591910,purple:8388736,rebeccapurple:6697881,red:16711680,rosybrown:12357519,royalblue:4286945,saddlebrown:9127187,salmon:16416882,sandybrown:16032864,seagreen:3050327,seashell:16774638,sienna:10506797,silver:12632256,skyblue:8900331,slateblue:6970061,slategray:7372944,slategrey:7372944,snow:16775930,springgreen:65407,steelblue:4620980,tan:13808780,teal:32896,thistle:14204888,tomato:16737095,turquoise:4251856,violet:15631086,wheat:16113331,white:16777215,whitesmoke:16119285,yellow:16776960,yellowgreen:10145074},Bn={h:0,s:0,l:0},Vn={h:0,s:0,l:0};function Hn(e,t,n){return n<0&&(n+=1),n>1&&--n,n<1/6?e+(t-e)*6*n:n<1/2?t:n<2/3?e+(t-e)*6*(2/3-n):e}var Y=class{constructor(e,t,n){return this.isColor=!0,this.r=1,this.g=1,this.b=1,this.set(e,t,n)}set(e,t,n){if(t===void 0&&n===void 0){let t=e;t&&t.isColor?this.copy(t):typeof t==`number`?this.setHex(t):typeof t==`string`&&this.setStyle(t)}else this.setRGB(e,t,n);return this}setScalar(e){return this.r=e,this.g=e,this.b=e,this}setHex(e,t=Xe){return e=Math.floor(e),this.r=(e>>16&255)/255,this.g=(e>>8&255)/255,this.b=(e&255)/255,q.colorSpaceToWorking(this,t),this}setRGB(e,t,n,r=q.workingColorSpace){return this.r=e,this.g=t,this.b=n,q.colorSpaceToWorking(this,r),this}setHSL(e,t,n,r=q.workingColorSpace){if(e=yt(e,1),t=U(t,0,1),n=U(n,0,1),t===0)this.r=this.g=this.b=n;else{let r=n<=.5?n*(1+t):n+t-n*t,i=2*n-r;this.r=Hn(i,r,e+1/3),this.g=Hn(i,r,e),this.b=Hn(i,r,e-1/3)}return q.colorSpaceToWorking(this,r),this}setStyle(e,t=Xe){function n(t){t!==void 0&&parseFloat(t)<1&&V(`Color: Alpha component of `+e+` will be ignored.`)}let r;if(r=/^(\w+)\(([^\)]*)\)/.exec(e)){let i,a=r[1],o=r[2];switch(a){case`rgb`:case`rgba`:if(i=/^\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*(\d*\.?\d+)\s*)?$/.exec(o))return n(i[4]),this.setRGB(Math.min(255,parseInt(i[1],10))/255,Math.min(255,parseInt(i[2],10))/255,Math.min(255,parseInt(i[3],10))/255,t);if(i=/^\s*(\d+)\%\s*,\s*(\d+)\%\s*,\s*(\d+)\%\s*(?:,\s*(\d*\.?\d+)\s*)?$/.exec(o))return n(i[4]),this.setRGB(Math.min(100,parseInt(i[1],10))/100,Math.min(100,parseInt(i[2],10))/100,Math.min(100,parseInt(i[3],10))/100,t);break;case`hsl`:case`hsla`:if(i=/^\s*(\d*\.?\d+)\s*,\s*(\d*\.?\d+)\%\s*,\s*(\d*\.?\d+)\%\s*(?:,\s*(\d*\.?\d+)\s*)?$/.exec(o))return n(i[4]),this.setHSL(parseFloat(i[1])/360,parseFloat(i[2])/100,parseFloat(i[3])/100,t);break;default:V(`Color: Unknown color model `+e)}}else if(r=/^\#([A-Fa-f\d]+)$/.exec(e)){let n=r[1],i=n.length;if(i===3)return this.setRGB(parseInt(n.charAt(0),16)/15,parseInt(n.charAt(1),16)/15,parseInt(n.charAt(2),16)/15,t);if(i===6)return this.setHex(parseInt(n,16),t);V(`Color: Invalid hex color `+e)}else if(e&&e.length>0)return this.setColorName(e,t);return this}setColorName(e,t=Xe){let n=zn[e.toLowerCase()];return n===void 0?V(`Color: Unknown color `+e):this.setHex(n,t),this}clone(){return new this.constructor(this.r,this.g,this.b)}copy(e){return this.r=e.r,this.g=e.g,this.b=e.b,this}copySRGBToLinear(e){return this.r=qt(e.r),this.g=qt(e.g),this.b=qt(e.b),this}copyLinearToSRGB(e){return this.r=Jt(e.r),this.g=Jt(e.g),this.b=Jt(e.b),this}convertSRGBToLinear(){return this.copySRGBToLinear(this),this}convertLinearToSRGB(){return this.copyLinearToSRGB(this),this}getHex(e=Xe){return q.workingToColorSpace(Un.copy(this),e),Math.round(U(Un.r*255,0,255))*65536+Math.round(U(Un.g*255,0,255))*256+Math.round(U(Un.b*255,0,255))}getHexString(e=Xe){return(`000000`+this.getHex(e).toString(16)).slice(-6)}getHSL(e,t=q.workingColorSpace){q.workingToColorSpace(Un.copy(this),t);let n=Un.r,r=Un.g,i=Un.b,a=Math.max(n,r,i),o=Math.min(n,r,i),s,c,l=(o+a)/2;if(o===a)s=0,c=0;else{let e=a-o;switch(c=l<=.5?e/(a+o):e/(2-a-o),a){case n:s=(r-i)/e+(r<i?6:0);break;case r:s=(i-n)/e+2;break;case i:s=(n-r)/e+4}s/=6}return e.h=s,e.s=c,e.l=l,e}getRGB(e,t=q.workingColorSpace){return q.workingToColorSpace(Un.copy(this),t),e.r=Un.r,e.g=Un.g,e.b=Un.b,e}getStyle(e=Xe){q.workingToColorSpace(Un.copy(this),e);let t=Un.r,n=Un.g,r=Un.b;return e===`srgb`?`rgb(${Math.round(t*255)},${Math.round(n*255)},${Math.round(r*255)})`:`color(${e} ${t.toFixed(3)} ${n.toFixed(3)} ${r.toFixed(3)})`}offsetHSL(e,t,n){return this.getHSL(Bn),this.setHSL(Bn.h+e,Bn.s+t,Bn.l+n)}add(e){return this.r+=e.r,this.g+=e.g,this.b+=e.b,this}addColors(e,t){return this.r=e.r+t.r,this.g=e.g+t.g,this.b=e.b+t.b,this}addScalar(e){return this.r+=e,this.g+=e,this.b+=e,this}sub(e){return this.r=Math.max(0,this.r-e.r),this.g=Math.max(0,this.g-e.g),this.b=Math.max(0,this.b-e.b),this}multiply(e){return this.r*=e.r,this.g*=e.g,this.b*=e.b,this}multiplyScalar(e){return this.r*=e,this.g*=e,this.b*=e,this}lerp(e,t){return this.r+=(e.r-this.r)*t,this.g+=(e.g-this.g)*t,this.b+=(e.b-this.b)*t,this}lerpColors(e,t,n){return this.r=e.r+(t.r-e.r)*n,this.g=e.g+(t.g-e.g)*n,this.b=e.b+(t.b-e.b)*n,this}lerpHSL(e,t){this.getHSL(Bn),e.getHSL(Vn);let n=St(Bn.h,Vn.h,t),r=St(Bn.s,Vn.s,t),i=St(Bn.l,Vn.l,t);return this.setHSL(n,r,i),this}setFromVector3(e){return this.r=e.x,this.g=e.y,this.b=e.z,this}applyMatrix3(e){let t=this.r,n=this.g,r=this.b,i=e.elements;return this.r=i[0]*t+i[3]*n+i[6]*r,this.g=i[1]*t+i[4]*n+i[7]*r,this.b=i[2]*t+i[5]*n+i[8]*r,this}equals(e){return e.r===this.r&&e.g===this.g&&e.b===this.b}fromArray(e,t=0){return this.r=e[t],this.g=e[t+1],this.b=e[t+2],this}toArray(e=[],t=0){return e[t]=this.r,e[t+1]=this.g,e[t+2]=this.b,e}fromBufferAttribute(e,t){return this.r=e.getX(t),this.g=e.getY(t),this.b=e.getZ(t),this}toJSON(){return this.getHex()}*[Symbol.iterator](){yield this.r,yield this.g,yield this.b}},Un=new Y;Y.NAMES=zn;var Wn=class e{constructor(e,t=25e-5){this.isFogExp2=!0,this.name=``,this.color=new Y(e),this.density=t}clone(){return new e(this.color,this.density)}toJSON(){return{type:`FogExp2`,name:this.name,color:this.color.getHex(),density:this.density}}},Gn=class extends Fn{constructor(){super(),this.isScene=!0,this.type=`Scene`,this.background=null,this.environment=null,this.fog=null,this.backgroundBlurriness=0,this.backgroundIntensity=1,this.backgroundRotation=new vn,this.environmentIntensity=1,this.environmentRotation=new vn,this.overrideMaterial=null,typeof __THREE_DEVTOOLS__<`u`&&__THREE_DEVTOOLS__.dispatchEvent(new CustomEvent(`observe`,{detail:this}))}copy(e,t){return super.copy(e,t),e.background!==null&&(this.background=e.background.clone()),e.environment!==null&&(this.environment=e.environment.clone()),e.fog!==null&&(this.fog=e.fog.clone()),this.backgroundBlurriness=e.backgroundBlurriness,this.backgroundIntensity=e.backgroundIntensity,this.backgroundRotation.copy(e.backgroundRotation),this.environmentIntensity=e.environmentIntensity,this.environmentRotation.copy(e.environmentRotation),e.overrideMaterial!==null&&(this.overrideMaterial=e.overrideMaterial.clone()),this.matrixAutoUpdate=e.matrixAutoUpdate,this}toJSON(e){let t=super.toJSON(e);return this.fog!==null&&(t.object.fog=this.fog.toJSON()),t.object.backgroundBlurriness=this.backgroundBlurriness,t.object.backgroundIntensity=this.backgroundIntensity,t.object.backgroundRotation=this.backgroundRotation.toArray(),t.object.environmentIntensity=this.environmentIntensity,t.object.environmentRotation=this.environmentRotation.toArray(),t}},Kn=new G,qn=new G,Jn=new G,Yn=new G,Xn=new G,Zn=new G,Qn=new G,$n=new G,er=new G,tr=new G,nr=new rn,rr=new rn,ir=new rn,ar=class e{constructor(e=new G,t=new G,n=new G){this.a=e,this.b=t,this.c=n}static getNormal(e,t,n,r){r.subVectors(n,t),Kn.subVectors(e,t),r.cross(Kn);let i=r.lengthSq();return i>0?r.multiplyScalar(1/Math.sqrt(i)):r.set(0,0,0)}static getBarycoord(e,t,n,r,i){Kn.subVectors(r,t),qn.subVectors(n,t),Jn.subVectors(e,t);let a=Kn.dot(Kn),o=Kn.dot(qn),s=Kn.dot(Jn),c=qn.dot(qn),l=qn.dot(Jn),u=a*c-o*o;if(u===0)return i.set(0,0,0),null;let d=1/u,f=(c*s-o*l)*d,p=(a*l-o*s)*d;return i.set(1-f-p,p,f)}static containsPoint(e,t,n,r){return this.getBarycoord(e,t,n,r,Yn)!==null&&Yn.x>=0&&Yn.y>=0&&Yn.x+Yn.y<=1}static getInterpolation(e,t,n,r,i,a,o,s){return this.getBarycoord(e,t,n,r,Yn)===null?(s.x=0,s.y=0,`z`in s&&(s.z=0),`w`in s&&(s.w=0),null):(s.setScalar(0),s.addScaledVector(i,Yn.x),s.addScaledVector(a,Yn.y),s.addScaledVector(o,Yn.z),s)}static getInterpolatedAttribute(e,t,n,r,i,a){return nr.setScalar(0),rr.setScalar(0),ir.setScalar(0),nr.fromBufferAttribute(e,t),rr.fromBufferAttribute(e,n),ir.fromBufferAttribute(e,r),a.setScalar(0),a.addScaledVector(nr,i.x),a.addScaledVector(rr,i.y),a.addScaledVector(ir,i.z),a}static isFrontFacing(e,t,n,r){return Kn.subVectors(n,t),qn.subVectors(e,t),Kn.cross(qn).dot(r)<0}set(e,t,n){return this.a.copy(e),this.b.copy(t),this.c.copy(n),this}setFromPointsAndIndices(e,t,n,r){return this.a.copy(e[t]),this.b.copy(e[n]),this.c.copy(e[r]),this}setFromAttributeAndIndices(e,t,n,r){return this.a.fromBufferAttribute(e,t),this.b.fromBufferAttribute(e,n),this.c.fromBufferAttribute(e,r),this}clone(){return new this.constructor().copy(this)}copy(e){return this.a.copy(e.a),this.b.copy(e.b),this.c.copy(e.c),this}getArea(){return Kn.subVectors(this.c,this.b),qn.subVectors(this.a,this.b),Kn.cross(qn).length()*.5}getMidpoint(e){return e.addVectors(this.a,this.b).add(this.c).multiplyScalar(1/3)}getNormal(t){return e.getNormal(this.a,this.b,this.c,t)}getPlane(e){return e.setFromCoplanarPoints(this.a,this.b,this.c)}getBarycoord(t,n){return e.getBarycoord(t,this.a,this.b,this.c,n)}getInterpolation(t,n,r,i,a){return e.getInterpolation(t,this.a,this.b,this.c,n,r,i,a)}containsPoint(t){return e.containsPoint(t,this.a,this.b,this.c)}isFrontFacing(t){return e.isFrontFacing(this.a,this.b,this.c,t)}intersectsBox(e){return e.intersectsTriangle(this)}closestPointToPoint(e,t){let n=this.a,r=this.b,i=this.c,a,o;Xn.subVectors(r,n),Zn.subVectors(i,n),$n.subVectors(e,n);let s=Xn.dot($n),c=Zn.dot($n);if(s<=0&&c<=0)return t.copy(n);er.subVectors(e,r);let l=Xn.dot(er),u=Zn.dot(er);if(l>=0&&u<=l)return t.copy(r);let d=s*u-l*c;if(d<=0&&s>=0&&l<=0)return a=s/(s-l),t.copy(n).addScaledVector(Xn,a);tr.subVectors(e,i);let f=Xn.dot(tr),p=Zn.dot(tr);if(p>=0&&f<=p)return t.copy(i);let m=f*c-s*p;if(m<=0&&c>=0&&p<=0)return o=c/(c-p),t.copy(n).addScaledVector(Zn,o);let h=l*p-f*u;if(h<=0&&u-l>=0&&f-p>=0)return Qn.subVectors(i,r),o=(u-l)/(u-l+(f-p)),t.copy(r).addScaledVector(Qn,o);let g=1/(h+m+d);return a=m*g,o=d*g,t.copy(n).addScaledVector(Xn,a).addScaledVector(Zn,o)}equals(e){return e.a.equals(this.a)&&e.b.equals(this.b)&&e.c.equals(this.c)}},or=class{constructor(e=new G(1/0,1/0,1/0),t=new G(-1/0,-1/0,-1/0)){this.isBox3=!0,this.min=e,this.max=t}set(e,t){return this.min.copy(e),this.max.copy(t),this}setFromArray(e){this.makeEmpty();for(let t=0,n=e.length;t<n;t+=3)this.expandByPoint(cr.fromArray(e,t));return this}setFromBufferAttribute(e){this.makeEmpty();for(let t=0,n=e.count;t<n;t++)this.expandByPoint(cr.fromBufferAttribute(e,t));return this}setFromPoints(e){this.makeEmpty();for(let t=0,n=e.length;t<n;t++)this.expandByPoint(e[t]);return this}setFromCenterAndSize(e,t){let n=cr.copy(t).multiplyScalar(.5);return this.min.copy(e).sub(n),this.max.copy(e).add(n),this}setFromObject(e,t=!1){return this.makeEmpty(),this.expandByObject(e,t)}clone(){return new this.constructor().copy(this)}copy(e){return this.min.copy(e.min),this.max.copy(e.max),this}makeEmpty(){return this.min.x=this.min.y=this.min.z=1/0,this.max.x=this.max.y=this.max.z=-1/0,this}isEmpty(){return this.max.x<this.min.x||this.max.y<this.min.y||this.max.z<this.min.z}getCenter(e){return this.isEmpty()?e.set(0,0,0):e.addVectors(this.min,this.max).multiplyScalar(.5)}getSize(e){return this.isEmpty()?e.set(0,0,0):e.subVectors(this.max,this.min)}expandByPoint(e){return this.min.min(e),this.max.max(e),this}expandByVector(e){return this.min.sub(e),this.max.add(e),this}expandByScalar(e){return this.min.addScalar(-e),this.max.addScalar(e),this}expandByObject(e,t=!1){e.updateWorldMatrix(!1,!1);let n=e.geometry;if(n!==void 0){let r=n.getAttribute(`position`);if(t===!0&&r!==void 0&&e.isInstancedMesh!==!0)for(let t=0,n=r.count;t<n;t++)e.isMesh===!0?e.getVertexPosition(t,cr):cr.fromBufferAttribute(r,t),cr.applyMatrix4(e.matrixWorld),this.expandByPoint(cr);else e.boundingBox===void 0?(n.boundingBox===null&&n.computeBoundingBox(),lr.copy(n.boundingBox)):(e.boundingBox===null&&e.computeBoundingBox(),lr.copy(e.boundingBox)),lr.applyMatrix4(e.matrixWorld),this.union(lr)}let r=e.children;for(let e=0,n=r.length;e<n;e++)this.expandByObject(r[e],t);return this}containsPoint(e){return e.x>=this.min.x&&e.x<=this.max.x&&e.y>=this.min.y&&e.y<=this.max.y&&e.z>=this.min.z&&e.z<=this.max.z}containsBox(e){return this.min.x<=e.min.x&&e.max.x<=this.max.x&&this.min.y<=e.min.y&&e.max.y<=this.max.y&&this.min.z<=e.min.z&&e.max.z<=this.max.z}getParameter(e,t){return t.set((e.x-this.min.x)/(this.max.x-this.min.x),(e.y-this.min.y)/(this.max.y-this.min.y),(e.z-this.min.z)/(this.max.z-this.min.z))}intersectsBox(e){return e.max.x>=this.min.x&&e.min.x<=this.max.x&&e.max.y>=this.min.y&&e.min.y<=this.max.y&&e.max.z>=this.min.z&&e.min.z<=this.max.z}intersectsSphere(e){return this.clampPoint(e.center,cr),cr.distanceToSquared(e.center)<=e.radius*e.radius}intersectsPlane(e){let t,n;return e.normal.x>0?(t=e.normal.x*this.min.x,n=e.normal.x*this.max.x):(t=e.normal.x*this.max.x,n=e.normal.x*this.min.x),e.normal.y>0?(t+=e.normal.y*this.min.y,n+=e.normal.y*this.max.y):(t+=e.normal.y*this.max.y,n+=e.normal.y*this.min.y),e.normal.z>0?(t+=e.normal.z*this.min.z,n+=e.normal.z*this.max.z):(t+=e.normal.z*this.max.z,n+=e.normal.z*this.min.z),t<=-e.constant&&n>=-e.constant}intersectsTriangle(e){if(this.isEmpty())return!1;this.getCenter(gr),_r.subVectors(this.max,gr),ur.subVectors(e.a,gr),dr.subVectors(e.b,gr),fr.subVectors(e.c,gr),pr.subVectors(dr,ur),mr.subVectors(fr,dr),hr.subVectors(ur,fr);let t=[0,-pr.z,pr.y,0,-mr.z,mr.y,0,-hr.z,hr.y,pr.z,0,-pr.x,mr.z,0,-mr.x,hr.z,0,-hr.x,-pr.y,pr.x,0,-mr.y,mr.x,0,-hr.y,hr.x,0];return!br(t,ur,dr,fr,_r)||(t=[1,0,0,0,1,0,0,0,1],!br(t,ur,dr,fr,_r))?!1:(vr.crossVectors(pr,mr),t=[vr.x,vr.y,vr.z],br(t,ur,dr,fr,_r))}clampPoint(e,t){return t.copy(e).clamp(this.min,this.max)}distanceToPoint(e){return this.clampPoint(e,cr).distanceTo(e)}getBoundingSphere(e){return this.isEmpty()?e.makeEmpty():(this.getCenter(e.center),e.radius=this.getSize(cr).length()*.5),e}intersect(e){return this.min.max(e.min),this.max.min(e.max),this.isEmpty()&&this.makeEmpty(),this}union(e){return this.min.min(e.min),this.max.max(e.max),this}applyMatrix4(e){return this.isEmpty()||(sr[0].set(this.min.x,this.min.y,this.min.z).applyMatrix4(e),sr[1].set(this.min.x,this.min.y,this.max.z).applyMatrix4(e),sr[2].set(this.min.x,this.max.y,this.min.z).applyMatrix4(e),sr[3].set(this.min.x,this.max.y,this.max.z).applyMatrix4(e),sr[4].set(this.max.x,this.min.y,this.min.z).applyMatrix4(e),sr[5].set(this.max.x,this.min.y,this.max.z).applyMatrix4(e),sr[6].set(this.max.x,this.max.y,this.min.z).applyMatrix4(e),sr[7].set(this.max.x,this.max.y,this.max.z).applyMatrix4(e),this.setFromPoints(sr)),this}translate(e){return this.min.add(e),this.max.add(e),this}equals(e){return e.min.equals(this.min)&&e.max.equals(this.max)}toJSON(){return{min:this.min.toArray(),max:this.max.toArray()}}fromJSON(e){return this.min.fromArray(e.min),this.max.fromArray(e.max),this}},sr=[new G,new G,new G,new G,new G,new G,new G,new G],cr=new G,lr=new or,ur=new G,dr=new G,fr=new G,pr=new G,mr=new G,hr=new G,gr=new G,_r=new G,vr=new G,yr=new G;function br(e,t,n,r,i){for(let a=0,o=e.length-3;a<=o;a+=3){yr.fromArray(e,a);let o=i.x*Math.abs(yr.x)+i.y*Math.abs(yr.y)+i.z*Math.abs(yr.z),s=t.dot(yr),c=n.dot(yr),l=r.dot(yr);if(Math.max(-Math.max(s,c,l),Math.min(s,c,l))>o)return!1}return!0}var xr=new G,Sr=new W,Cr=0,X=class extends pt{constructor(e,t,n=!1){if(super(),Array.isArray(e))throw TypeError(`THREE.BufferAttribute: array should be a Typed Array.`);this.isBufferAttribute=!0,Object.defineProperty(this,"id",{value:Cr++}),this.name=``,this.array=e,this.itemSize=t,this.count=e===void 0?0:e.length/t,this.normalized=n,this.usage=tt,this.updateRanges=[],this.gpuType=A,this.version=0}onUploadCallback(){}set needsUpdate(e){e===!0&&this.version++}setUsage(e){return this.usage=e,this}addUpdateRange(e,t){this.updateRanges.push({start:e,count:t})}clearUpdateRanges(){this.updateRanges.length=0}copy(e){return this.name=e.name,this.array=new e.array.constructor(e.array),this.itemSize=e.itemSize,this.count=e.count,this.normalized=e.normalized,this.usage=e.usage,this.gpuType=e.gpuType,this}copyAt(e,t,n){e*=this.itemSize,n*=t.itemSize;for(let r=0,i=this.itemSize;r<i;r++)this.array[e+r]=t.array[n+r];return this}copyArray(e){return this.array.set(e),this}applyMatrix3(e){if(this.itemSize===2)for(let t=0,n=this.count;t<n;t++)Sr.fromBufferAttribute(this,t),Sr.applyMatrix3(e),this.setXY(t,Sr.x,Sr.y);else if(this.itemSize===3)for(let t=0,n=this.count;t<n;t++)xr.fromBufferAttribute(this,t),xr.applyMatrix3(e),this.setXYZ(t,xr.x,xr.y,xr.z);return this}applyMatrix4(e){for(let t=0,n=this.count;t<n;t++)xr.fromBufferAttribute(this,t),xr.applyMatrix4(e),this.setXYZ(t,xr.x,xr.y,xr.z);return this}applyNormalMatrix(e){for(let t=0,n=this.count;t<n;t++)xr.fromBufferAttribute(this,t),xr.applyNormalMatrix(e),this.setXYZ(t,xr.x,xr.y,xr.z);return this}transformDirection(e){for(let t=0,n=this.count;t<n;t++)xr.fromBufferAttribute(this,t),xr.transformDirection(e),this.setXYZ(t,xr.x,xr.y,xr.z);return this}set(e,t=0){return this.array.set(e,t),this}getComponent(e,t){let n=this.array[e*this.itemSize+t];return this.normalized&&(n=Lt(n,this.array)),n}setComponent(e,t,n){return this.normalized&&(n=Rt(n,this.array)),this.array[e*this.itemSize+t]=n,this}getX(e){let t=this.array[e*this.itemSize];return this.normalized&&(t=Lt(t,this.array)),t}setX(e,t){return this.normalized&&(t=Rt(t,this.array)),this.array[e*this.itemSize]=t,this}getY(e){let t=this.array[e*this.itemSize+1];return this.normalized&&(t=Lt(t,this.array)),t}setY(e,t){return this.normalized&&(t=Rt(t,this.array)),this.array[e*this.itemSize+1]=t,this}getZ(e){let t=this.array[e*this.itemSize+2];return this.normalized&&(t=Lt(t,this.array)),t}setZ(e,t){return this.normalized&&(t=Rt(t,this.array)),this.array[e*this.itemSize+2]=t,this}getW(e){let t=this.array[e*this.itemSize+3];return this.normalized&&(t=Lt(t,this.array)),t}setW(e,t){return this.normalized&&(t=Rt(t,this.array)),this.array[e*this.itemSize+3]=t,this}setXY(e,t,n){return e*=this.itemSize,this.normalized&&(t=Rt(t,this.array),n=Rt(n,this.array)),this.array[e+0]=t,this.array[e+1]=n,this}setXYZ(e,t,n,r){return e*=this.itemSize,this.normalized&&(t=Rt(t,this.array),n=Rt(n,this.array),r=Rt(r,this.array)),this.array[e+0]=t,this.array[e+1]=n,this.array[e+2]=r,this}setXYZW(e,t,n,r,i){return e*=this.itemSize,this.normalized&&(t=Rt(t,this.array),n=Rt(n,this.array),r=Rt(r,this.array),i=Rt(i,this.array)),this.array[e+0]=t,this.array[e+1]=n,this.array[e+2]=r,this.array[e+3]=i,this}onUpload(e){return this.onUploadCallback=e,this}clone(){return new this.constructor(this.array,this.itemSize).copy(this)}toJSON(){let e={itemSize:this.itemSize,type:this.array.constructor.name,array:Array.from(this.array),normalized:this.normalized};return e.name=this.name,e.usage=this.usage,e.gpuType=this.gpuType,e}dispose(){this.dispatchEvent({type:`dispose`})}},wr=class extends X{constructor(e,t,n){super(new Uint16Array(e),t,n)}},Tr=class extends X{constructor(e,t,n){super(new Uint32Array(e),t,n)}},Er=class extends X{constructor(e,t,n){super(new Float32Array(e),t,n)}},Dr=new or,Or=new G,kr=new G,Ar=class{constructor(e=new G,t=-1){this.isSphere=!0,this.center=e,this.radius=t}set(e,t){return this.center.copy(e),this.radius=t,this}setFromPoints(e,t){let n=this.center;t===void 0?Dr.setFromPoints(e).getCenter(n):n.copy(t);let r=0;for(let t=0,i=e.length;t<i;t++)r=Math.max(r,n.distanceToSquared(e[t]));return this.radius=Math.sqrt(r),this}copy(e){return this.center.copy(e.center),this.radius=e.radius,this}isEmpty(){return this.radius<0}makeEmpty(){return this.center.set(0,0,0),this.radius=-1,this}containsPoint(e){return e.distanceToSquared(this.center)<=this.radius*this.radius}distanceToPoint(e){return e.distanceTo(this.center)-this.radius}intersectsSphere(e){let t=this.radius+e.radius;return e.center.distanceToSquared(this.center)<=t*t}intersectsBox(e){return e.intersectsSphere(this)}intersectsPlane(e){return Math.abs(e.distanceToPoint(this.center))<=this.radius}clampPoint(e,t){let n=this.center.distanceToSquared(e);return t.copy(e),n>this.radius*this.radius&&(t.sub(this.center).normalize(),t.multiplyScalar(this.radius).add(this.center)),t}getBoundingBox(e){return this.isEmpty()?(e.makeEmpty(),e):(e.set(this.center,this.center),e.expandByScalar(this.radius),e)}applyMatrix4(e){return this.center.applyMatrix4(e),this.radius*=e.getMaxScaleOnAxis(),this}translate(e){return this.center.add(e),this}expandByPoint(e){if(this.isEmpty())return this.center.copy(e),this.radius=0,this;Or.subVectors(e,this.center);let t=Or.lengthSq();if(t>this.radius*this.radius){let e=Math.sqrt(t),n=(e-this.radius)*.5;this.center.addScaledVector(Or,n/e),this.radius+=n}return this}union(e){return e.isEmpty()?this:this.isEmpty()?(this.copy(e),this):(this.center.equals(e.center)===!0?this.radius=Math.max(this.radius,e.radius):(kr.subVectors(e.center,this.center).setLength(e.radius),this.expandByPoint(Or.copy(e.center).add(kr)),this.expandByPoint(Or.copy(e.center).sub(kr))),this)}equals(e){return e.center.equals(this.center)&&e.radius===this.radius}clone(){return new this.constructor().copy(this)}toJSON(){return{radius:this.radius,center:this.center.toArray()}}fromJSON(e){return this.radius=e.radius,this.center.fromArray(e.center),this}},jr=0,Mr=new J,Nr=new Fn,Pr=new G,Fr=new or,Ir=new or,Lr=new G,Rr=class e extends pt{constructor(){super(),this.isBufferGeometry=!0,Object.defineProperty(this,"id",{value:jr++}),this.uuid=vt(),this.name=``,this.type=`BufferGeometry`,this.index=null,this.indirect=null,this.indirectOffset=0,this.attributes={},this.morphAttributes={},this.morphTargetsRelative=!1,this.groups=[],this.boundingBox=null,this.boundingSphere=null,this.drawRange={start:0,count:1/0},this.userData={},this._transformed=!1}getIndex(){return this.index}setIndex(e){return this.index=Array.isArray(e)?new(rt(e)?Tr:wr)(e,1):e,this}setIndirect(e,t=0){return this.indirect=e,this.indirectOffset=t,this}getIndirect(){return this.indirect}getAttribute(e){return this.attributes[e]}setAttribute(e,t){return this.attributes[e]=t,this}deleteAttribute(e){return delete this.attributes[e],this}hasAttribute(e){return this.attributes[e]!==void 0}addGroup(e,t,n=0){this.groups.push({start:e,count:t,materialIndex:n})}clearGroups(){this.groups=[]}setDrawRange(e,t){this.drawRange.start=e,this.drawRange.count=t}applyMatrix4(e){let t=this.attributes.position;t!==void 0&&(t.applyMatrix4(e),t.needsUpdate=!0);let n=this.attributes.normal;if(n!==void 0){let t=new K().getNormalMatrix(e);n.applyNormalMatrix(t),n.needsUpdate=!0}let r=this.attributes.tangent;return r!==void 0&&(r.transformDirection(e),r.needsUpdate=!0),this.boundingBox!==null&&this.computeBoundingBox(),this.boundingSphere!==null&&this.computeBoundingSphere(),this._transformed=!0,this}applyQuaternion(e){return Mr.makeRotationFromQuaternion(e),this.applyMatrix4(Mr),this}rotateX(e){return Mr.makeRotationX(e),this.applyMatrix4(Mr),this}rotateY(e){return Mr.makeRotationY(e),this.applyMatrix4(Mr),this}rotateZ(e){return Mr.makeRotationZ(e),this.applyMatrix4(Mr),this}translate(e,t,n){return Mr.makeTranslation(e,t,n),this.applyMatrix4(Mr),this}scale(e,t,n){return Mr.makeScale(e,t,n),this.applyMatrix4(Mr),this}lookAt(e){return Nr.lookAt(e),Nr.updateMatrix(),this.applyMatrix4(Nr.matrix),this}center(){return this.computeBoundingBox(),this.boundingBox.getCenter(Pr).negate(),this.translate(Pr.x,Pr.y,Pr.z),this}setFromPoints(e){let t=this.getAttribute(`position`);if(t===void 0){let t=[];for(let n=0,r=e.length;n<r;n++){let r=e[n];t.push(r.x,r.y,r.z||0)}this.setAttribute(`position`,new Er(t,3))}else{let n=Math.min(e.length,t.count);for(let r=0;r<n;r++){let n=e[r];t.setXYZ(r,n.x,n.y,n.z||0)}e.length>t.count&&V(`BufferGeometry: Buffer size too small for points data. Use .dispose() and create a new geometry.`),t.needsUpdate=!0}return this}computeBoundingBox(){this.boundingBox===null&&(this.boundingBox=new or);let e=this.attributes.position,t=this.morphAttributes.position;if(e&&e.isGLBufferAttribute)H(`BufferGeometry.computeBoundingBox(): GLBufferAttribute requires a manual bounding box.`,this),this.boundingBox.set(new G(-1/0,-1/0,-1/0),new G(1/0,1/0,1/0));else{if(e!==void 0){if(this.boundingBox.setFromBufferAttribute(e),t)for(let e=0,n=t.length;e<n;e++){let n=t[e];Fr.setFromBufferAttribute(n),this.morphTargetsRelative?(Lr.addVectors(this.boundingBox.min,Fr.min),this.boundingBox.expandByPoint(Lr),Lr.addVectors(this.boundingBox.max,Fr.max),this.boundingBox.expandByPoint(Lr)):(this.boundingBox.expandByPoint(Fr.min),this.boundingBox.expandByPoint(Fr.max))}}else this.boundingBox.makeEmpty();(isNaN(this.boundingBox.min.x)||isNaN(this.boundingBox.min.y)||isNaN(this.boundingBox.min.z))&&H(`BufferGeometry.computeBoundingBox(): Computed min/max have NaN values. The "position" attribute is likely to have NaN values.`,this)}}computeBoundingSphere(){this.boundingSphere===null&&(this.boundingSphere=new Ar);let e=this.attributes.position,t=this.morphAttributes.position;if(e&&e.isGLBufferAttribute)H(`BufferGeometry.computeBoundingSphere(): GLBufferAttribute requires a manual bounding sphere.`,this),this.boundingSphere.set(new G,1/0);else if(e){let n=this.boundingSphere.center;if(Fr.setFromBufferAttribute(e),t)for(let e=0,n=t.length;e<n;e++){let n=t[e];Ir.setFromBufferAttribute(n),this.morphTargetsRelative?(Lr.addVectors(Fr.min,Ir.min),Fr.expandByPoint(Lr),Lr.addVectors(Fr.max,Ir.max),Fr.expandByPoint(Lr)):(Fr.expandByPoint(Ir.min),Fr.expandByPoint(Ir.max))}Fr.getCenter(n);let r=0;for(let t=0,i=e.count;t<i;t++)Lr.fromBufferAttribute(e,t),r=Math.max(r,n.distanceToSquared(Lr));if(t)for(let i=0,a=t.length;i<a;i++){let a=t[i],o=this.morphTargetsRelative;for(let t=0,i=a.count;t<i;t++)Lr.fromBufferAttribute(a,t),o&&(Pr.fromBufferAttribute(e,t),Lr.add(Pr)),r=Math.max(r,n.distanceToSquared(Lr))}this.boundingSphere.radius=Math.sqrt(r),isNaN(this.boundingSphere.radius)&&H(`BufferGeometry.computeBoundingSphere(): Computed radius is NaN. The "position" attribute is likely to have NaN values.`,this)}}computeTangents(){let e=this.index,t=this.attributes;if(e===null||t.position===void 0||t.normal===void 0||t.uv===void 0){H(`BufferGeometry: .computeTangents() failed. Missing required attributes (index, position, normal or uv)`);return}let n=t.position,r=t.normal,i=t.uv,a=this.getAttribute(`tangent`);(a===void 0||a.count!==n.count)&&(a=new X(new Float32Array(4*n.count),4),this.setAttribute(`tangent`,a));let o=[],s=[];for(let e=0;e<n.count;e++)o[e]=new G,s[e]=new G;let c=new G,l=new G,u=new G,d=new W,f=new W,p=new W,m=new G,h=new G;function g(e,t,r){c.fromBufferAttribute(n,e),l.fromBufferAttribute(n,t),u.fromBufferAttribute(n,r),d.fromBufferAttribute(i,e),f.fromBufferAttribute(i,t),p.fromBufferAttribute(i,r),l.sub(c),u.sub(c),f.sub(d),p.sub(d);let a=1/(f.x*p.y-p.x*f.y);isFinite(a)&&(m.copy(l).multiplyScalar(p.y).addScaledVector(u,-f.y).multiplyScalar(a),h.copy(u).multiplyScalar(f.x).addScaledVector(l,-p.x).multiplyScalar(a),o[e].add(m),o[t].add(m),o[r].add(m),s[e].add(h),s[t].add(h),s[r].add(h))}let _=this.groups;_.length===0&&(_=[{start:0,count:e.count}]);for(let t=0,n=_.length;t<n;++t){let n=_[t],r=n.start,i=n.count;for(let t=r,n=r+i;t<n;t+=3)g(e.getX(t+0),e.getX(t+1),e.getX(t+2))}let v=new G,y=new G,b=new G,x=new G;function S(e){b.fromBufferAttribute(r,e),x.copy(b);let t=o[e];v.copy(t),v.sub(b.multiplyScalar(b.dot(t))).normalize(),y.crossVectors(x,t);let n=y.dot(s[e])<0?-1:1;a.setXYZW(e,v.x,v.y,v.z,n)}for(let t=0,n=_.length;t<n;++t){let n=_[t],r=n.start,i=n.count;for(let t=r,n=r+i;t<n;t+=3)S(e.getX(t+0)),S(e.getX(t+1)),S(e.getX(t+2))}this._transformed=!0}computeVertexNormals(){let e=this.index,t=this.getAttribute(`position`);if(t!==void 0){let n=this.getAttribute(`normal`);if(n===void 0||n.count!==t.count)n=new X(new Float32Array(t.count*3),3),this.setAttribute(`normal`,n);else for(let e=0,t=n.count;e<t;e++)n.setXYZ(e,0,0,0);let r=new G,i=new G,a=new G,o=new G,s=new G,c=new G,l=new G,u=new G;if(e)for(let d=0,f=e.count;d<f;d+=3){let f=e.getX(d+0),p=e.getX(d+1),m=e.getX(d+2);r.fromBufferAttribute(t,f),i.fromBufferAttribute(t,p),a.fromBufferAttribute(t,m),l.subVectors(a,i),u.subVectors(r,i),l.cross(u),o.fromBufferAttribute(n,f),s.fromBufferAttribute(n,p),c.fromBufferAttribute(n,m),o.add(l),s.add(l),c.add(l),n.setXYZ(f,o.x,o.y,o.z),n.setXYZ(p,s.x,s.y,s.z),n.setXYZ(m,c.x,c.y,c.z)}else for(let e=0,o=t.count;e<o;e+=3)r.fromBufferAttribute(t,e+0),i.fromBufferAttribute(t,e+1),a.fromBufferAttribute(t,e+2),l.subVectors(a,i),u.subVectors(r,i),l.cross(u),n.setXYZ(e+0,l.x,l.y,l.z),n.setXYZ(e+1,l.x,l.y,l.z),n.setXYZ(e+2,l.x,l.y,l.z);this.normalizeNormals(),n.needsUpdate=!0}}normalizeNormals(){let e=this.attributes.normal;for(let t=0,n=e.count;t<n;t++)Lr.fromBufferAttribute(e,t),Lr.normalize(),e.setXYZ(t,Lr.x,Lr.y,Lr.z)}toNonIndexed(){function t(e,t){let n=e.array,r=e.itemSize,i=e.normalized,a=new n.constructor(t.length*r),o=0,s=0;for(let i=0,c=t.length;i<c;i++){o=e.isInterleavedBufferAttribute?t[i]*e.data.stride+e.offset:t[i]*r;for(let e=0;e<r;e++)a[s++]=n[o++]}return new X(a,r,i)}if(this.index===null)return V(`BufferGeometry.toNonIndexed(): BufferGeometry is already non-indexed.`),this;let n=new e,r=this.index.array,i=this.attributes;for(let e in i){let a=i[e],o=t(a,r);n.setAttribute(e,o)}let a=this.morphAttributes;for(let e in a){let i=[],o=a[e];for(let e=0,n=o.length;e<n;e++){let n=o[e],a=t(n,r);i.push(a)}n.morphAttributes[e]=i}n.morphTargetsRelative=this.morphTargetsRelative;let o=this.groups;for(let e=0,t=o.length;e<t;e++){let t=o[e];n.addGroup(t.start,t.count,t.materialIndex)}return n}toJSON(){let e={metadata:{version:4.7,type:`BufferGeometry`,generator:`BufferGeometry.toJSON`}};if(e.uuid=this.uuid,e.type=this.parameters!==void 0&&this._transformed===!0?`BufferGeometry`:this.type,e.name=this.name,Object.keys(this.userData).length>0&&(e.userData=this.userData),this.parameters!==void 0&&this._transformed!==!0){let t=this.parameters;for(let n in t)t[n]!==void 0&&(e[n]=t[n]);return e}e.data={attributes:{}};let t=this.index;t!==null&&(e.data.index={type:t.array.constructor.name,array:Array.prototype.slice.call(t.array)});let n=this.attributes;for(let t in n){let r=n[t];e.data.attributes[t]=r.toJSON(e.data)}let r={},i=!1;for(let t in this.morphAttributes){let n=this.morphAttributes[t],a=[];for(let t=0,r=n.length;t<r;t++){let r=n[t];a.push(r.toJSON(e.data))}a.length>0&&(r[t]=a,i=!0)}i&&(e.data.morphAttributes=r,e.data.morphTargetsRelative=this.morphTargetsRelative);let a=this.groups;a.length>0&&(e.data.groups=JSON.parse(JSON.stringify(a)));let o=this.boundingSphere;return o!==null&&(e.data.boundingSphere=o.toJSON()),e}clone(){return new this.constructor().copy(this)}copy(e){this.index=null,this.attributes={},this.morphAttributes={},this.groups=[],this.boundingBox=null,this.boundingSphere=null;let t={};this.name=e.name;let n=e.index;n!==null&&this.setIndex(n.clone());let r=e.attributes;for(let e in r){let n=r[e];this.setAttribute(e,n.clone(t))}let i=e.morphAttributes;for(let e in i){let n=[],r=i[e];for(let e=0,i=r.length;e<i;e++)n.push(r[e].clone(t));this.morphAttributes[e]=n}this.morphTargetsRelative=e.morphTargetsRelative;let a=e.groups;for(let e=0,t=a.length;e<t;e++){let t=a[e];this.addGroup(t.start,t.count,t.materialIndex)}let o=e.boundingBox;o!==null&&(this.boundingBox=o.clone());let s=e.boundingSphere;return s!==null&&(this.boundingSphere=s.clone()),this.drawRange.start=e.drawRange.start,this.drawRange.count=e.drawRange.count,this.userData=e.userData,this._transformed=e._transformed,this}dispose(){this.dispatchEvent({type:`dispose`})}},zr=new G,Br=new G,Vr=new K,Hr=class{constructor(e=new G(1,0,0),t=0){this.isPlane=!0,this.normal=e,this.constant=t}set(e,t){return this.normal.copy(e),this.constant=t,this}setComponents(e,t,n,r){return this.normal.set(e,t,n),this.constant=r,this}setFromNormalAndCoplanarPoint(e,t){return this.normal.copy(e),this.constant=-t.dot(this.normal),this}setFromCoplanarPoints(e,t,n){let r=zr.subVectors(n,t).cross(Br.subVectors(e,t)).normalize();return this.setFromNormalAndCoplanarPoint(r,e),this}copy(e){return this.normal.copy(e.normal),this.constant=e.constant,this}normalize(){let e=1/this.normal.length();return this.normal.multiplyScalar(e),this.constant*=e,this}negate(){return this.constant*=-1,this.normal.negate(),this}distanceToPoint(e){return this.normal.dot(e)+this.constant}distanceToSphere(e){return this.distanceToPoint(e.center)-e.radius}projectPoint(e,t){return t.copy(e).addScaledVector(this.normal,-this.distanceToPoint(e))}intersectLine(e,t,n=!0){let r=e.delta(zr),i=this.normal.dot(r);if(i===0)return this.distanceToPoint(e.start)===0?t.copy(e.start):null;let a=-(e.start.dot(this.normal)+this.constant)/i;return n===!0&&(a<0||a>1)?null:t.copy(e.start).addScaledVector(r,a)}intersectsLine(e){let t=this.distanceToPoint(e.start),n=this.distanceToPoint(e.end);return t<0&&n>0||n<0&&t>0}intersectsBox(e){return e.intersectsPlane(this)}intersectsSphere(e){return e.intersectsPlane(this)}coplanarPoint(e){return e.copy(this.normal).multiplyScalar(-this.constant)}applyMatrix4(e,t){let n=t||Vr.getNormalMatrix(e),r=this.coplanarPoint(zr).applyMatrix4(e),i=this.normal.applyMatrix3(n).normalize();return this.constant=-r.dot(i),this}translate(e){return this.constant-=e.dot(this.normal),this}equals(e){return e.normal.equals(this.normal)&&e.constant===this.constant}clone(){return new this.constructor().copy(this)}toJSON(){return{normal:this.normal.toArray(),constant:this.constant}}fromJSON(e){return this.normal.fromArray(e.normal),this.constant=e.constant,this}},Ur=0,Wr=class extends pt{constructor(){super(),this.isMaterial=!0,Object.defineProperty(this,"id",{value:Ur++}),this.uuid=vt(),this.name=``,this.type=`Material`,this.blending=1,this.side=0,this.vertexColors=!1,this.opacity=1,this.transparent=!1,this.alphaHash=!1,this.blendSrc=204,this.blendDst=205,this.blendEquation=100,this.blendSrcAlpha=null,this.blendDstAlpha=null,this.blendEquationAlpha=null,this.blendColor=new Y(0,0,0),this.blendAlpha=0,this.depthFunc=3,this.depthTest=!0,this.depthWrite=!0,this.stencilWriteMask=255,this.stencilFunc=519,this.stencilRef=0,this.stencilFuncMask=255,this.stencilFail=et,this.stencilZFail=et,this.stencilZPass=et,this.stencilWrite=!1,this.clippingPlanes=null,this.clipIntersection=!1,this.clipShadows=!1,this.shadowSide=null,this.colorWrite=!0,this.precision=null,this.polygonOffset=!1,this.polygonOffsetFactor=0,this.polygonOffsetUnits=0,this.dithering=!1,this.alphaToCoverage=!1,this.premultipliedAlpha=!1,this.forceSinglePass=!1,this.allowOverride=!0,this.visible=!0,this.toneMapped=!0,this.userData={},this.version=0,this._alphaTest=0}get alphaTest(){return this._alphaTest}set alphaTest(e){this._alphaTest>0!=e>0&&this.version++,this._alphaTest=e}onBeforeRender(){}onBeforeCompile(){}customProgramCacheKey(){return this.onBeforeCompile.toString()}setValues(e){if(e!==void 0)for(let t in e){let n=e[t];if(n===void 0){V(`Material: parameter '${t}' has value of undefined.`);continue}let r=this[t];r===void 0?V(`Material: '${t}' is not a property of THREE.${this.type}.`):r&&r.isColor?r.set(n):r&&r.isVector2&&n&&n.isVector2||r&&r.isEuler&&n&&n.isEuler||r&&r.isVector3&&n&&n.isVector3?r.copy(n):this[t]=n}}toJSON(e){let t=e===void 0||typeof e==`string`;t&&(e={textures:{},images:{}});let n={metadata:{version:4.7,type:`Material`,generator:`Material.toJSON`}};n.uuid=this.uuid,n.type=this.type,n.blending=this.blending,n.side=this.side,n.shadowSide=this.shadowSide,n.vertexColors=this.vertexColors,n.opacity=this.opacity,n.transparent=this.transparent,n.blendSrc=this.blendSrc,n.blendDst=this.blendDst,n.blendEquation=this.blendEquation,n.blendSrcAlpha=this.blendSrcAlpha,n.blendDstAlpha=this.blendDstAlpha,n.blendEquationAlpha=this.blendEquationAlpha,n.blendColor=this.blendColor.getHex(),n.blendAlpha=this.blendAlpha,n.depthFunc=this.depthFunc,n.depthTest=this.depthTest,n.depthWrite=this.depthWrite,n.colorWrite=this.colorWrite,n.clipIntersection=this.clipIntersection,n.clipShadows=this.clipShadows,n.stencilWriteMask=this.stencilWriteMask,n.stencilFunc=this.stencilFunc,n.stencilRef=this.stencilRef,n.stencilFuncMask=this.stencilFuncMask,n.stencilFail=this.stencilFail,n.stencilZFail=this.stencilZFail,n.stencilZPass=this.stencilZPass,n.stencilWrite=this.stencilWrite,n.polygonOffset=this.polygonOffset,n.polygonOffsetFactor=this.polygonOffsetFactor,n.polygonOffsetUnits=this.polygonOffsetUnits,n.dithering=this.dithering,n.alphaTest=this.alphaTest,n.alphaHash=this.alphaHash,n.alphaToCoverage=this.alphaToCoverage,n.premultipliedAlpha=this.premultipliedAlpha,n.forceSinglePass=this.forceSinglePass,n.allowOverride=this.allowOverride,n.visible=this.visible,n.toneMapped=this.toneMapped,n.name=this.name,this.color&&this.color.isColor&&(n.color=this.color.getHex()),this.roughness!==void 0&&(n.roughness=this.roughness),this.metalness!==void 0&&(n.metalness=this.metalness),this.sheen!==void 0&&(n.sheen=this.sheen),this.sheenColor&&this.sheenColor.isColor&&(n.sheenColor=this.sheenColor.getHex()),this.sheenRoughness!==void 0&&(n.sheenRoughness=this.sheenRoughness),this.emissive&&this.emissive.isColor&&(n.emissive=this.emissive.getHex()),this.emissiveIntensity!==void 0&&(n.emissiveIntensity=this.emissiveIntensity),this.specular&&this.specular.isColor&&(n.specular=this.specular.getHex()),this.specularIntensity!==void 0&&(n.specularIntensity=this.specularIntensity),this.specularColor&&this.specularColor.isColor&&(n.specularColor=this.specularColor.getHex()),this.shininess!==void 0&&(n.shininess=this.shininess),this.clearcoat!==void 0&&(n.clearcoat=this.clearcoat),this.clearcoatRoughness!==void 0&&(n.clearcoatRoughness=this.clearcoatRoughness),this.clearcoatMap&&this.clearcoatMap.isTexture&&(n.clearcoatMap=this.clearcoatMap.toJSON(e).uuid),this.clearcoatRoughnessMap&&this.clearcoatRoughnessMap.isTexture&&(n.clearcoatRoughnessMap=this.clearcoatRoughnessMap.toJSON(e).uuid),this.clearcoatNormalMap&&this.clearcoatNormalMap.isTexture&&(n.clearcoatNormalMap=this.clearcoatNormalMap.toJSON(e).uuid,n.clearcoatNormalScale=this.clearcoatNormalScale.toArray()),this.sheenColorMap&&this.sheenColorMap.isTexture&&(n.sheenColorMap=this.sheenColorMap.toJSON(e).uuid),this.sheenRoughnessMap&&this.sheenRoughnessMap.isTexture&&(n.sheenRoughnessMap=this.sheenRoughnessMap.toJSON(e).uuid),this.dispersion!==void 0&&(n.dispersion=this.dispersion),this.retroreflectivity!==void 0&&(n.retroreflectivity=this.retroreflectivity),this.iridescence!==void 0&&(n.iridescence=this.iridescence),this.iridescenceIOR!==void 0&&(n.iridescenceIOR=this.iridescenceIOR),this.iridescenceThicknessRange!==void 0&&(n.iridescenceThicknessRange=this.iridescenceThicknessRange),this.iridescenceMap&&this.iridescenceMap.isTexture&&(n.iridescenceMap=this.iridescenceMap.toJSON(e).uuid),this.iridescenceThicknessMap&&this.iridescenceThicknessMap.isTexture&&(n.iridescenceThicknessMap=this.iridescenceThicknessMap.toJSON(e).uuid),this.anisotropy!==void 0&&(n.anisotropy=this.anisotropy),this.anisotropyRotation!==void 0&&(n.anisotropyRotation=this.anisotropyRotation),this.anisotropyMap&&this.anisotropyMap.isTexture&&(n.anisotropyMap=this.anisotropyMap.toJSON(e).uuid),this.map&&this.map.isTexture&&(n.map=this.map.toJSON(e).uuid),this.matcap&&this.matcap.isTexture&&(n.matcap=this.matcap.toJSON(e).uuid),this.alphaMap&&this.alphaMap.isTexture&&(n.alphaMap=this.alphaMap.toJSON(e).uuid),this.lightMap&&this.lightMap.isTexture&&(n.lightMap=this.lightMap.toJSON(e).uuid,n.lightMapIntensity=this.lightMapIntensity),this.aoMap&&this.aoMap.isTexture&&(n.aoMap=this.aoMap.toJSON(e).uuid,n.aoMapIntensity=this.aoMapIntensity),this.bumpMap&&this.bumpMap.isTexture&&(n.bumpMap=this.bumpMap.toJSON(e).uuid,n.bumpScale=this.bumpScale),this.normalMap&&this.normalMap.isTexture&&(n.normalMap=this.normalMap.toJSON(e).uuid,n.normalMapType=this.normalMapType,n.normalScale=this.normalScale.toArray()),this.displacementMap&&this.displacementMap.isTexture&&(n.displacementMap=this.displacementMap.toJSON(e).uuid,n.displacementScale=this.displacementScale,n.displacementBias=this.displacementBias),this.roughnessMap&&this.roughnessMap.isTexture&&(n.roughnessMap=this.roughnessMap.toJSON(e).uuid),this.metalnessMap&&this.metalnessMap.isTexture&&(n.metalnessMap=this.metalnessMap.toJSON(e).uuid),this.emissiveMap&&this.emissiveMap.isTexture&&(n.emissiveMap=this.emissiveMap.toJSON(e).uuid),this.specularMap&&this.specularMap.isTexture&&(n.specularMap=this.specularMap.toJSON(e).uuid),this.specularIntensityMap&&this.specularIntensityMap.isTexture&&(n.specularIntensityMap=this.specularIntensityMap.toJSON(e).uuid),this.specularColorMap&&this.specularColorMap.isTexture&&(n.specularColorMap=this.specularColorMap.toJSON(e).uuid),this.envMap&&this.envMap.isTexture&&(n.envMap=this.envMap.toJSON(e).uuid,this.combine!==void 0&&(n.combine=this.combine)),this.envMapRotation!==void 0&&(n.envMapRotation=this.envMapRotation.toArray()),this.envMapIntensity!==void 0&&(n.envMapIntensity=this.envMapIntensity),this.reflectivity!==void 0&&(n.reflectivity=this.reflectivity),this.refractionRatio!==void 0&&(n.refractionRatio=this.refractionRatio),this.gradientMap&&this.gradientMap.isTexture&&(n.gradientMap=this.gradientMap.toJSON(e).uuid),this.transmission!==void 0&&(n.transmission=this.transmission),this.transmissionMap&&this.transmissionMap.isTexture&&(n.transmissionMap=this.transmissionMap.toJSON(e).uuid),this.thickness!==void 0&&(n.thickness=this.thickness),this.thicknessMap&&this.thicknessMap.isTexture&&(n.thicknessMap=this.thicknessMap.toJSON(e).uuid),this.attenuationDistance!==void 0&&(n.attenuationDistance=this.attenuationDistance),this.attenuationColor!==void 0&&(n.attenuationColor=this.attenuationColor.getHex()),this.size!==void 0&&(n.size=this.size),this.sizeAttenuation!==void 0&&(n.sizeAttenuation=this.sizeAttenuation),Array.isArray(this.clippingPlanes)&&this.clippingPlanes.length>0&&(n.clippingPlanes=this.clippingPlanes.map(e=>e.toJSON())),this.rotation!==void 0&&(n.rotation=this.rotation),this.depthPacking!==void 0&&(n.depthPacking=this.depthPacking),this.linewidth!==void 0&&(n.linewidth=this.linewidth),this.linecap!==void 0&&(n.linecap=this.linecap),this.linejoin!==void 0&&(n.linejoin=this.linejoin),this.dashSize!==void 0&&(n.dashSize=this.dashSize),this.gapSize!==void 0&&(n.gapSize=this.gapSize),this.scale!==void 0&&(n.scale=this.scale),this.wireframe!==void 0&&(n.wireframe=this.wireframe),this.wireframeLinewidth!==void 0&&(n.wireframeLinewidth=this.wireframeLinewidth),this.wireframeLinecap!==void 0&&(n.wireframeLinecap=this.wireframeLinecap),this.wireframeLinejoin!==void 0&&(n.wireframeLinejoin=this.wireframeLinejoin),this.flatShading!==void 0&&(n.flatShading=this.flatShading),this.fog!==void 0&&(n.fog=this.fog),Object.keys(this.userData).length>0&&(n.userData=this.userData);function r(e){let t=[];for(let n in e){let r=e[n];delete r.metadata,t.push(r)}return t}if(t){let t=r(e.textures),i=r(e.images);t.length>0&&(n.textures=t),i.length>0&&(n.images=i)}return n}fromJSON(e,t){if(e.uuid!==void 0&&(this.uuid=e.uuid),e.name!==void 0&&(this.name=e.name),e.color!==void 0&&this.color!==void 0&&this.color.setHex(e.color),e.roughness!==void 0&&(this.roughness=e.roughness),e.metalness!==void 0&&(this.metalness=e.metalness),e.sheen!==void 0&&(this.sheen=e.sheen),e.sheenColor!==void 0&&(this.sheenColor=new Y().setHex(e.sheenColor)),e.sheenRoughness!==void 0&&(this.sheenRoughness=e.sheenRoughness),e.emissive!==void 0&&this.emissive!==void 0&&this.emissive.setHex(e.emissive),e.specular!==void 0&&this.specular!==void 0&&this.specular.setHex(e.specular),e.specularIntensity!==void 0&&(this.specularIntensity=e.specularIntensity),e.specularColor!==void 0&&this.specularColor!==void 0&&this.specularColor.setHex(e.specularColor),e.shininess!==void 0&&(this.shininess=e.shininess),e.clearcoat!==void 0&&(this.clearcoat=e.clearcoat),e.clearcoatRoughness!==void 0&&(this.clearcoatRoughness=e.clearcoatRoughness),e.dispersion!==void 0&&(this.dispersion=e.dispersion),e.retroreflectivity!==void 0&&(this.retroreflectivity=e.retroreflectivity),e.iridescence!==void 0&&(this.iridescence=e.iridescence),e.iridescenceIOR!==void 0&&(this.iridescenceIOR=e.iridescenceIOR),e.iridescenceThicknessRange!==void 0&&(this.iridescenceThicknessRange=e.iridescenceThicknessRange),e.transmission!==void 0&&(this.transmission=e.transmission),e.thickness!==void 0&&(this.thickness=e.thickness),e.attenuationDistance!==void 0&&(this.attenuationDistance=e.attenuationDistance),e.attenuationColor!==void 0&&this.attenuationColor!==void 0&&this.attenuationColor.setHex(e.attenuationColor),e.anisotropy!==void 0&&(this.anisotropy=e.anisotropy),e.anisotropyRotation!==void 0&&(this.anisotropyRotation=e.anisotropyRotation),e.fog!==void 0&&(this.fog=e.fog),e.flatShading!==void 0&&(this.flatShading=e.flatShading),e.blending!==void 0&&(this.blending=e.blending),e.combine!==void 0&&(this.combine=e.combine),e.side!==void 0&&(this.side=e.side),e.shadowSide!==void 0&&(this.shadowSide=e.shadowSide),e.opacity!==void 0&&(this.opacity=e.opacity),e.transparent!==void 0&&(this.transparent=e.transparent),e.alphaTest!==void 0&&(this.alphaTest=e.alphaTest),e.alphaHash!==void 0&&(this.alphaHash=e.alphaHash),e.depthFunc!==void 0&&(this.depthFunc=e.depthFunc),e.depthTest!==void 0&&(this.depthTest=e.depthTest),e.depthWrite!==void 0&&(this.depthWrite=e.depthWrite),e.colorWrite!==void 0&&(this.colorWrite=e.colorWrite),e.clippingPlanes!==void 0&&(this.clippingPlanes=e.clippingPlanes.map(e=>new Hr().fromJSON(e))),e.clipIntersection!==void 0&&(this.clipIntersection=e.clipIntersection),e.clipShadows!==void 0&&(this.clipShadows=e.clipShadows),e.depthPacking!==void 0&&(this.depthPacking=e.depthPacking),e.blendSrc!==void 0&&(this.blendSrc=e.blendSrc),e.blendDst!==void 0&&(this.blendDst=e.blendDst),e.blendEquation!==void 0&&(this.blendEquation=e.blendEquation),e.blendSrcAlpha!==void 0&&(this.blendSrcAlpha=e.blendSrcAlpha),e.blendDstAlpha!==void 0&&(this.blendDstAlpha=e.blendDstAlpha),e.blendEquationAlpha!==void 0&&(this.blendEquationAlpha=e.blendEquationAlpha),e.blendColor!==void 0&&this.blendColor!==void 0&&this.blendColor.setHex(e.blendColor),e.blendAlpha!==void 0&&(this.blendAlpha=e.blendAlpha),e.stencilWriteMask!==void 0&&(this.stencilWriteMask=e.stencilWriteMask),e.stencilFunc!==void 0&&(this.stencilFunc=e.stencilFunc),e.stencilRef!==void 0&&(this.stencilRef=e.stencilRef),e.stencilFuncMask!==void 0&&(this.stencilFuncMask=e.stencilFuncMask),e.stencilFail!==void 0&&(this.stencilFail=e.stencilFail),e.stencilZFail!==void 0&&(this.stencilZFail=e.stencilZFail),e.stencilZPass!==void 0&&(this.stencilZPass=e.stencilZPass),e.stencilWrite!==void 0&&(this.stencilWrite=e.stencilWrite),e.wireframe!==void 0&&(this.wireframe=e.wireframe),e.wireframeLinewidth!==void 0&&(this.wireframeLinewidth=e.wireframeLinewidth),e.wireframeLinecap!==void 0&&(this.wireframeLinecap=e.wireframeLinecap),e.wireframeLinejoin!==void 0&&(this.wireframeLinejoin=e.wireframeLinejoin),e.rotation!==void 0&&(this.rotation=e.rotation),e.linewidth!==void 0&&(this.linewidth=e.linewidth),e.linecap!==void 0&&(this.linecap=e.linecap),e.linejoin!==void 0&&(this.linejoin=e.linejoin),e.dashSize!==void 0&&(this.dashSize=e.dashSize),e.gapSize!==void 0&&(this.gapSize=e.gapSize),e.scale!==void 0&&(this.scale=e.scale),e.polygonOffset!==void 0&&(this.polygonOffset=e.polygonOffset),e.polygonOffsetFactor!==void 0&&(this.polygonOffsetFactor=e.polygonOffsetFactor),e.polygonOffsetUnits!==void 0&&(this.polygonOffsetUnits=e.polygonOffsetUnits),e.dithering!==void 0&&(this.dithering=e.dithering),e.alphaToCoverage!==void 0&&(this.alphaToCoverage=e.alphaToCoverage),e.premultipliedAlpha!==void 0&&(this.premultipliedAlpha=e.premultipliedAlpha),e.forceSinglePass!==void 0&&(this.forceSinglePass=e.forceSinglePass),e.allowOverride!==void 0&&(this.allowOverride=e.allowOverride),e.visible!==void 0&&(this.visible=e.visible),e.toneMapped!==void 0&&(this.toneMapped=e.toneMapped),e.userData!==void 0&&(this.userData=e.userData),e.vertexColors!==void 0&&(this.vertexColors=typeof e.vertexColors==`number`?e.vertexColors>0:e.vertexColors),e.size!==void 0&&(this.size=e.size),e.sizeAttenuation!==void 0&&(this.sizeAttenuation=e.sizeAttenuation),e.map!==void 0&&(this.map=t[e.map]||null),e.matcap!==void 0&&(this.matcap=t[e.matcap]||null),e.alphaMap!==void 0&&(this.alphaMap=t[e.alphaMap]||null),e.bumpMap!==void 0&&(this.bumpMap=t[e.bumpMap]||null),e.bumpScale!==void 0&&(this.bumpScale=e.bumpScale),e.normalMap!==void 0&&(this.normalMap=t[e.normalMap]||null),e.normalMapType!==void 0&&(this.normalMapType=e.normalMapType),e.normalScale!==void 0){let t=e.normalScale;Array.isArray(t)===!1&&(t=[t,t]),this.normalScale=new W().fromArray(t)}return e.displacementMap!==void 0&&(this.displacementMap=t[e.displacementMap]||null),e.displacementScale!==void 0&&(this.displacementScale=e.displacementScale),e.displacementBias!==void 0&&(this.displacementBias=e.displacementBias),e.roughnessMap!==void 0&&(this.roughnessMap=t[e.roughnessMap]||null),e.metalnessMap!==void 0&&(this.metalnessMap=t[e.metalnessMap]||null),e.emissiveMap!==void 0&&(this.emissiveMap=t[e.emissiveMap]||null),e.emissiveIntensity!==void 0&&(this.emissiveIntensity=e.emissiveIntensity),e.specularMap!==void 0&&(this.specularMap=t[e.specularMap]||null),e.specularIntensityMap!==void 0&&(this.specularIntensityMap=t[e.specularIntensityMap]||null),e.specularColorMap!==void 0&&(this.specularColorMap=t[e.specularColorMap]||null),e.envMap!==void 0&&(this.envMap=t[e.envMap]||null),e.envMapRotation!==void 0&&this.envMapRotation.fromArray(e.envMapRotation),e.envMapIntensity!==void 0&&(this.envMapIntensity=e.envMapIntensity),e.reflectivity!==void 0&&(this.reflectivity=e.reflectivity),e.refractionRatio!==void 0&&(this.refractionRatio=e.refractionRatio),e.lightMap!==void 0&&(this.lightMap=t[e.lightMap]||null),e.lightMapIntensity!==void 0&&(this.lightMapIntensity=e.lightMapIntensity),e.aoMap!==void 0&&(this.aoMap=t[e.aoMap]||null),e.aoMapIntensity!==void 0&&(this.aoMapIntensity=e.aoMapIntensity),e.gradientMap!==void 0&&(this.gradientMap=t[e.gradientMap]||null),e.clearcoatMap!==void 0&&(this.clearcoatMap=t[e.clearcoatMap]||null),e.clearcoatRoughnessMap!==void 0&&(this.clearcoatRoughnessMap=t[e.clearcoatRoughnessMap]||null),e.clearcoatNormalMap!==void 0&&(this.clearcoatNormalMap=t[e.clearcoatNormalMap]||null),e.clearcoatNormalScale!==void 0&&(this.clearcoatNormalScale=new W().fromArray(e.clearcoatNormalScale)),e.iridescenceMap!==void 0&&(this.iridescenceMap=t[e.iridescenceMap]||null),e.iridescenceThicknessMap!==void 0&&(this.iridescenceThicknessMap=t[e.iridescenceThicknessMap]||null),e.transmissionMap!==void 0&&(this.transmissionMap=t[e.transmissionMap]||null),e.thicknessMap!==void 0&&(this.thicknessMap=t[e.thicknessMap]||null),e.anisotropyMap!==void 0&&(this.anisotropyMap=t[e.anisotropyMap]||null),e.sheenColorMap!==void 0&&(this.sheenColorMap=t[e.sheenColorMap]||null),e.sheenRoughnessMap!==void 0&&(this.sheenRoughnessMap=t[e.sheenRoughnessMap]||null),this}clone(){return new this.constructor().copy(this)}copy(e){this.name=e.name,this.blending=e.blending,this.side=e.side,this.vertexColors=e.vertexColors,this.opacity=e.opacity,this.transparent=e.transparent,this.blendSrc=e.blendSrc,this.blendDst=e.blendDst,this.blendEquation=e.blendEquation,this.blendSrcAlpha=e.blendSrcAlpha,this.blendDstAlpha=e.blendDstAlpha,this.blendEquationAlpha=e.blendEquationAlpha,this.blendColor.copy(e.blendColor),this.blendAlpha=e.blendAlpha,this.depthFunc=e.depthFunc,this.depthTest=e.depthTest,this.depthWrite=e.depthWrite,this.stencilWriteMask=e.stencilWriteMask,this.stencilFunc=e.stencilFunc,this.stencilRef=e.stencilRef,this.stencilFuncMask=e.stencilFuncMask,this.stencilFail=e.stencilFail,this.stencilZFail=e.stencilZFail,this.stencilZPass=e.stencilZPass,this.stencilWrite=e.stencilWrite;let t=e.clippingPlanes,n=null;if(t!==null){let e=t.length;n=Array(e);for(let r=0;r!==e;++r)n[r]=t[r].clone()}return this.clippingPlanes=n,this.clipIntersection=e.clipIntersection,this.clipShadows=e.clipShadows,this.shadowSide=e.shadowSide,this.colorWrite=e.colorWrite,this.precision=e.precision,this.polygonOffset=e.polygonOffset,this.polygonOffsetFactor=e.polygonOffsetFactor,this.polygonOffsetUnits=e.polygonOffsetUnits,this.dithering=e.dithering,this.alphaTest=e.alphaTest,this.alphaHash=e.alphaHash,this.alphaToCoverage=e.alphaToCoverage,this.premultipliedAlpha=e.premultipliedAlpha,this.forceSinglePass=e.forceSinglePass,this.allowOverride=e.allowOverride,this.visible=e.visible,this.toneMapped=e.toneMapped,this.userData=JSON.parse(JSON.stringify(e.userData)),this}dispose(){this.dispatchEvent({type:`dispose`})}set needsUpdate(e){e===!0&&this.version++}},Gr=new G,Kr=new G,qr=new G,Jr=new G,Yr=class{constructor(e=new G,t=new G(0,0,-1)){this.origin=e,this.direction=t}set(e,t){return this.origin.copy(e),this.direction.copy(t),this}copy(e){return this.origin.copy(e.origin),this.direction.copy(e.direction),this}at(e,t){return t.copy(this.origin).addScaledVector(this.direction,e)}lookAt(e){return this.direction.copy(e).sub(this.origin).normalize(),this}recast(e){return this.origin.copy(this.at(e,Gr)),this}closestPointToPoint(e,t){t.subVectors(e,this.origin);let n=t.dot(this.direction);return n<0?t.copy(this.origin):t.copy(this.origin).addScaledVector(this.direction,n)}distanceToPoint(e){return Math.sqrt(this.distanceSqToPoint(e))}distanceSqToPoint(e){let t=Gr.subVectors(e,this.origin).dot(this.direction);return t<0?this.origin.distanceToSquared(e):(Gr.copy(this.origin).addScaledVector(this.direction,t),Gr.distanceToSquared(e))}distanceSqToSegment(e,t,n,r){Kr.copy(e).add(t).multiplyScalar(.5),qr.copy(t).sub(e).normalize(),Jr.copy(this.origin).sub(Kr);let i=e.distanceTo(t)*.5,a=-this.direction.dot(qr),o=Jr.dot(this.direction),s=-Jr.dot(qr),c=Jr.lengthSq(),l=Math.abs(1-a*a),u,d,f,p;if(l>0){if(u=a*s-o,d=a*o-s,p=i*l,u>=0){if(d>=-p){if(d<=p){let e=1/l;u*=e,d*=e,f=u*(u+a*d+2*o)+d*(a*u+d+2*s)+c}else d=i,u=Math.max(0,-(a*d+o)),f=-u*u+d*(d+2*s)+c}else d=-i,u=Math.max(0,-(a*d+o)),f=-u*u+d*(d+2*s)+c}else d<=-p?(u=Math.max(0,-(-a*i+o)),d=u>0?-i:Math.min(Math.max(-i,-s),i),f=-u*u+d*(d+2*s)+c):d<=p?(u=0,d=Math.min(Math.max(-i,-s),i),f=d*(d+2*s)+c):(u=Math.max(0,-(a*i+o)),d=u>0?i:Math.min(Math.max(-i,-s),i),f=-u*u+d*(d+2*s)+c)}else d=a>0?-i:i,u=Math.max(0,-(a*d+o)),f=-u*u+d*(d+2*s)+c;return n&&n.copy(this.origin).addScaledVector(this.direction,u),r&&r.copy(Kr).addScaledVector(qr,d),f}intersectSphere(e,t){if(e.radius<0)return null;Gr.subVectors(e.center,this.origin);let n=Gr.dot(this.direction),r=Gr.dot(Gr)-n*n,i=e.radius*e.radius;if(r>i)return null;let a=Math.sqrt(i-r),o=n-a,s=n+a;return s<0?null:o<0?this.at(s,t):this.at(o,t)}intersectsSphere(e){return e.radius<0?!1:this.distanceSqToPoint(e.center)<=e.radius*e.radius}distanceToPlane(e){let t=e.normal.dot(this.direction);if(t===0)return e.distanceToPoint(this.origin)===0?0:null;let n=-(this.origin.dot(e.normal)+e.constant)/t;return n>=0?n:null}intersectPlane(e,t){let n=this.distanceToPlane(e);return n===null?null:this.at(n,t)}intersectsPlane(e){let t=e.distanceToPoint(this.origin);return t===0||e.normal.dot(this.direction)*t<0}intersectBox(e,t){let n,r,i,a,o,s,c=1/this.direction.x,l=1/this.direction.y,u=1/this.direction.z,d=this.origin;return c>=0?(n=(e.min.x-d.x)*c,r=(e.max.x-d.x)*c):(n=(e.max.x-d.x)*c,r=(e.min.x-d.x)*c),l>=0?(i=(e.min.y-d.y)*l,a=(e.max.y-d.y)*l):(i=(e.max.y-d.y)*l,a=(e.min.y-d.y)*l),n>a||i>r||((i>n||isNaN(n))&&(n=i),(a<r||isNaN(r))&&(r=a),u>=0?(o=(e.min.z-d.z)*u,s=(e.max.z-d.z)*u):(o=(e.max.z-d.z)*u,s=(e.min.z-d.z)*u),n>s||o>r)||((o>n||n!==n)&&(n=o),(s<r||r!==r)&&(r=s),r<0)?null:this.at(n>=0?n:r,t)}intersectsBox(e){return this.intersectBox(e,Gr)!==null}intersectTriangle(e,t,n,r,i){let a=this.origin,o=this.direction,s=o.x,c=o.y,l=o.z,u=e.x-a.x,d=e.y-a.y,f=e.z-a.z,p=t.x-a.x,m=t.y-a.y,h=t.z-a.z,g=n.x-a.x,_=n.y-a.y,v=n.z-a.z,y=Math.abs(s),b=Math.abs(c),x=Math.abs(l),S,C,w,T,E,D,O,k,A,j,ee,M;if(y>=b&&y>=x?(w=s,D=u,A=p,M=g,s>=0?(S=c,C=l,T=d,E=f,O=m,k=h,j=_,ee=v):(S=l,C=c,T=f,E=d,O=h,k=m,j=v,ee=_)):b>=x?(w=c,D=d,A=m,M=_,c>=0?(S=l,C=s,T=f,E=u,O=h,k=p,j=v,ee=g):(S=s,C=l,T=u,E=f,O=p,k=h,j=g,ee=v)):(w=l,D=f,A=h,M=v,l>=0?(S=s,C=c,T=u,E=d,O=p,k=m,j=g,ee=_):(S=c,C=s,T=d,E=u,O=m,k=p,j=_,ee=g)),w===0)return null;let te=S/w,N=C/w,ne=1/w,re=T-te*D,ie=E-N*D,ae=O-te*A,oe=k-N*A,P=j-te*M,se=ee-N*M,ce=P*oe-se*ae,F=re*se-ie*P,le=ae*ie-oe*re;if(r){if(ce<0||F<0||le<0)return null}else if((ce<0||F<0||le<0)&&(ce>0||F>0||le>0))return null;let ue=ce+F+le;if(ue===0)return null;let de=ne*(ce*D+F*A+le*M);return(ue>0?de<0:de>0)?null:this.at(de/ue,i)}applyMatrix4(e){return this.origin.applyMatrix4(e),this.direction.transformDirection(e),this}equals(e){return e.origin.equals(this.origin)&&e.direction.equals(this.direction)}clone(){return new this.constructor().copy(this)}},Xr=class extends Wr{constructor(e){super(),this.isMeshBasicMaterial=!0,this.type=`MeshBasicMaterial`,this.color=new Y(16777215),this.map=null,this.lightMap=null,this.lightMapIntensity=1,this.aoMap=null,this.aoMapIntensity=1,this.specularMap=null,this.alphaMap=null,this.envMap=null,this.envMapRotation=new vn,this.combine=0,this.reflectivity=1,this.refractionRatio=.98,this.wireframe=!1,this.wireframeLinewidth=1,this.wireframeLinecap=`round`,this.wireframeLinejoin=`round`,this.fog=!0,this.setValues(e)}copy(e){return super.copy(e),this.color.copy(e.color),this.map=e.map,this.lightMap=e.lightMap,this.lightMapIntensity=e.lightMapIntensity,this.aoMap=e.aoMap,this.aoMapIntensity=e.aoMapIntensity,this.specularMap=e.specularMap,this.alphaMap=e.alphaMap,this.envMap=e.envMap,this.envMapRotation.copy(e.envMapRotation),this.combine=e.combine,this.reflectivity=e.reflectivity,this.refractionRatio=e.refractionRatio,this.wireframe=e.wireframe,this.wireframeLinewidth=e.wireframeLinewidth,this.wireframeLinecap=e.wireframeLinecap,this.wireframeLinejoin=e.wireframeLinejoin,this.fog=e.fog,this}},Zr=new J,Qr=new Yr,$r=new Ar,ei=new G,ti=new G,ni=new G,ri=new G,ii=new G,ai=new G,oi=new G,si=new G,ci=class extends Fn{constructor(e=new Rr,t=new Xr){super(),this.isMesh=!0,this.type=`Mesh`,this.geometry=e,this.material=t,this.morphTargetDictionary=void 0,this.morphTargetInfluences=void 0,this.count=1,this.updateMorphTargets()}copy(e,t){return super.copy(e,t),e.morphTargetInfluences!==void 0&&(this.morphTargetInfluences=e.morphTargetInfluences.slice()),e.morphTargetDictionary!==void 0&&(this.morphTargetDictionary=Object.assign({},e.morphTargetDictionary)),this.material=Array.isArray(e.material)?e.material.slice():e.material,this.geometry=e.geometry,this}updateMorphTargets(){let e=this.geometry.morphAttributes,t=Object.keys(e);if(t.length>0){let n=e[t[0]];if(n!==void 0){this.morphTargetInfluences=[],this.morphTargetDictionary={};for(let e=0,t=n.length;e<t;e++){let t=n[e].name||String(e);this.morphTargetInfluences.push(0),this.morphTargetDictionary[t]=e}}}}getVertexPosition(e,t){let n=this.geometry,r=n.attributes.position,i=n.morphAttributes.position,a=n.morphTargetsRelative;t.fromBufferAttribute(r,e);let o=this.morphTargetInfluences;if(i&&o){ai.set(0,0,0);for(let n=0,r=i.length;n<r;n++){let r=o[n],s=i[n];r!==0&&(ii.fromBufferAttribute(s,e),a?ai.addScaledVector(ii,r):ai.addScaledVector(ii.sub(t),r))}t.add(ai)}return t}intersectsFrustum(e){return e.intersectsObject(this)}raycast(e,t){let n=this.geometry,r=this.material,i=this.matrixWorld;r!==void 0&&(n.boundingSphere===null&&n.computeBoundingSphere(),$r.copy(n.boundingSphere),$r.applyMatrix4(i),Qr.copy(e.ray).recast(e.near),!($r.containsPoint(Qr.origin)===!1&&(Qr.intersectSphere($r,ei)===null||Qr.origin.distanceToSquared(ei)>(e.far-e.near)**2))&&(Zr.copy(i).invert(),Qr.copy(e.ray).applyMatrix4(Zr),(n.boundingBox===null||Qr.intersectsBox(n.boundingBox)!==!1)&&this._computeIntersections(e,t,Qr)))}_computeIntersections(e,t,n){let r,i=this.geometry,a=this.material,o=i.index,s=i.attributes.position,c=i.attributes.uv,l=i.attributes.uv1,u=i.attributes.normal,d=i.groups,f=i.drawRange;if(o!==null){if(Array.isArray(a))for(let i=0,s=d.length;i<s;i++){let s=d[i],p=a[s.materialIndex],m=Math.max(s.start,f.start),h=Math.min(o.count,Math.min(s.start+s.count,f.start+f.count));for(let i=m,a=h;i<a;i+=3){let a=o.getX(i),d=o.getX(i+1),f=o.getX(i+2);r=ui(this,p,e,n,c,l,u,a,d,f),r&&(r.faceIndex=Math.floor(i/3),r.face.materialIndex=s.materialIndex,t.push(r))}}else{let i=Math.max(0,f.start),s=Math.min(o.count,f.start+f.count);for(let d=i,f=s;d<f;d+=3){let i=o.getX(d),s=o.getX(d+1),f=o.getX(d+2);r=ui(this,a,e,n,c,l,u,i,s,f),r&&(r.faceIndex=Math.floor(d/3),t.push(r))}}}else if(s!==void 0){if(Array.isArray(a))for(let i=0,o=d.length;i<o;i++){let o=d[i],p=a[o.materialIndex],m=Math.max(o.start,f.start),h=Math.min(s.count,Math.min(o.start+o.count,f.start+f.count));for(let i=m,a=h;i<a;i+=3){let a=i,s=i+1,d=i+2;r=ui(this,p,e,n,c,l,u,a,s,d),r&&(r.faceIndex=Math.floor(i/3),r.face.materialIndex=o.materialIndex,t.push(r))}}else{let i=Math.max(0,f.start),o=Math.min(s.count,f.start+f.count);for(let s=i,d=o;s<d;s+=3){let i=s,o=s+1,d=s+2;r=ui(this,a,e,n,c,l,u,i,o,d),r&&(r.faceIndex=Math.floor(s/3),t.push(r))}}}}};function li(e,t,n,r,i,a,o,s){let c;if(c=t.side===1?r.intersectTriangle(o,a,i,!0,s):r.intersectTriangle(i,a,o,t.side===0,s),c===null)return null;si.copy(s),si.applyMatrix4(e.matrixWorld);let l=n.ray.origin.distanceTo(si);return l<n.near||l>n.far?null:{distance:l,point:si.clone(),object:e}}function ui(e,t,n,r,i,a,o,s,c,l){e.getVertexPosition(s,ti),e.getVertexPosition(c,ni),e.getVertexPosition(l,ri);let u=li(e,t,n,r,ti,ni,ri,oi);if(u){let e=new G;ar.getBarycoord(oi,ti,ni,ri,e),i&&(u.uv=ar.getInterpolatedAttribute(i,s,c,l,e,new W)),a&&(u.uv1=ar.getInterpolatedAttribute(a,s,c,l,e,new W)),o&&(u.normal=ar.getInterpolatedAttribute(o,s,c,l,e,new G),u.normal.dot(r.direction)>0&&u.normal.multiplyScalar(-1));let t={a:s,b:c,c:l,normal:new G,materialIndex:0};ar.getNormal(ti,ni,ri,t.normal),u.face=t,u.barycoord=e}return u}var di=class extends nn{constructor(e=null,t=1,n=1,r,i,a,o,s,c=v,l=v,u,d){super(null,a,o,s,c,l,r,i,u,d),this.isDataTexture=!0,this.image={data:e,width:t,height:n},this.generateMipmaps=!1,this.flipY=!1,this.unpackAlignment=1}},fi=class extends X{constructor(e,t,n,r=1){super(e,t,n),this.isInstancedBufferAttribute=!0,this.meshPerAttribute=r}copy(e){return super.copy(e),this.meshPerAttribute=e.meshPerAttribute,this}toJSON(){let e=super.toJSON();return e.meshPerAttribute=this.meshPerAttribute,e.isInstancedBufferAttribute=!0,e}},pi=new J,mi=new J,hi=[],gi=new or,_i=new J,vi=new ci,yi=new Ar,bi=class extends ci{constructor(e,t,n){super(e,t),this.isInstancedMesh=!0,this.instanceMatrix=new fi(new Float32Array(n*16),16),this.instanceColor=null,this.morphTexture=null,this.count=n,this.boundingBox=null,this.boundingSphere=null;for(let e=0;e<n;e++)this.setMatrixAt(e,_i)}computeBoundingBox(){let e=this.geometry,t=this.count;this.boundingBox===null&&(this.boundingBox=new or),e.boundingBox===null&&e.computeBoundingBox(),this.boundingBox.makeEmpty();for(let n=0;n<t;n++)this.getMatrixAt(n,pi),gi.copy(e.boundingBox).applyMatrix4(pi),this.boundingBox.union(gi)}computeBoundingSphere(){let e=this.geometry,t=this.count;this.boundingSphere===null&&(this.boundingSphere=new Ar),e.boundingSphere===null&&e.computeBoundingSphere(),this.boundingSphere.makeEmpty();for(let n=0;n<t;n++)this.getMatrixAt(n,pi),yi.copy(e.boundingSphere).applyMatrix4(pi),this.boundingSphere.union(yi)}copy(e,t){return super.copy(e,t),this.instanceMatrix.copy(e.instanceMatrix),e.morphTexture!==null&&(this.morphTexture=e.morphTexture.clone()),e.instanceColor!==null&&(this.instanceColor=e.instanceColor.clone()),this.count=e.count,e.boundingBox!==null&&(this.boundingBox=e.boundingBox.clone()),e.boundingSphere!==null&&(this.boundingSphere=e.boundingSphere.clone()),this}getColorAt(e,t){return this.instanceColor===null?t.setRGB(1,1,1):t.fromArray(this.instanceColor.array,e*3)}getMatrixAt(e,t){return t.fromArray(this.instanceMatrix.array,e*16)}getMorphAt(e,t){let n=t.morphTargetInfluences,r=this.morphTexture.source.data.data,i=e*(n.length+1)+1;for(let e=0;e<n.length;e++)n[e]=r[i+e]}raycast(e,t){let n=this.matrixWorld,r=this.count;if(vi.geometry=this.geometry,vi.material=this.material,vi.material!==void 0&&(this.boundingSphere===null&&this.computeBoundingSphere(),yi.copy(this.boundingSphere),yi.applyMatrix4(n),e.ray.intersectsSphere(yi)!==!1))for(let i=0;i<r;i++){this.getMatrixAt(i,pi),mi.multiplyMatrices(n,pi),vi.matrixWorld=mi,vi.raycast(e,hi);for(let e=0,n=hi.length;e<n;e++){let n=hi[e];n.instanceId=i,n.object=this,t.push(n)}hi.length=0}}setColorAt(e,t){return this.instanceColor===null&&(this.instanceColor=new fi(new Float32Array(this.instanceMatrix.count*3).fill(1),3)),t.toArray(this.instanceColor.array,e*3),this}setMatrixAt(e,t){return t.toArray(this.instanceMatrix.array,e*16),this}setMorphAt(e,t){let n=t.morphTargetInfluences,r=n.length+1;this.morphTexture===null&&(this.morphTexture=new di(new Float32Array(r*this.count),r,this.count,se,A));let i=this.morphTexture.source.data.data,a=0;for(let e=0;e<n.length;e++)a+=n[e];let o=this.geometry.morphTargetsRelative?1:1-a,s=r*e;return i[s]=o,i.set(n,s+1),this}updateMorphTargets(){}dispose(){super.dispose(),this.morphTexture!==null&&(this.morphTexture.dispose(),this.morphTexture=null)}},xi=new Ar,Si=new W(.5,.5),Ci=new G,wi=class{constructor(e=new Hr,t=new Hr,n=new Hr,r=new Hr,i=new Hr,a=new Hr){this.planes=[e,t,n,r,i,a]}set(e,t,n,r,i,a){let o=this.planes;return o[0].copy(e),o[1].copy(t),o[2].copy(n),o[3].copy(r),o[4].copy(i),o[5].copy(a),this}copy(e){let t=this.planes;for(let n=0;n<6;n++)t[n].copy(e.planes[n]);return this}setFromProjectionMatrix(e,t=nt,n=!1){let r=this.planes,i=e.elements,a=i[0],o=i[1],s=i[2],c=i[3],l=i[4],u=i[5],d=i[6],f=i[7],p=i[8],m=i[9],h=i[10],g=i[11],_=i[12],v=i[13],y=i[14],b=i[15];if(r[0].setComponents(c-a,f-l,g-p,b-_).normalize(),r[1].setComponents(c+a,f+l,g+p,b+_).normalize(),r[2].setComponents(c+o,f+u,g+m,b+v).normalize(),r[3].setComponents(c-o,f-u,g-m,b-v).normalize(),n)r[4].setComponents(s,d,h,y).normalize(),r[5].setComponents(c-s,f-d,g-h,b-y).normalize();else if(r[4].setComponents(c-s,f-d,g-h,b-y).normalize(),t===2e3)r[5].setComponents(c+s,f+d,g+h,b+y).normalize();else if(t===2001)r[5].setComponents(s,d,h,y).normalize();else throw Error(`THREE.Frustum.setFromProjectionMatrix(): Invalid coordinate system: `+t);return this}intersectsObject(e){if(e.boundingSphere!==void 0)e.boundingSphere===null&&e.computeBoundingSphere(),xi.copy(e.boundingSphere).applyMatrix4(e.matrixWorld);else{let t=e.geometry;t.boundingSphere===null&&t.computeBoundingSphere(),xi.copy(t.boundingSphere).applyMatrix4(e.matrixWorld)}return this.intersectsSphere(xi)}intersectsSprite(e){return xi.center.set(0,0,0),xi.radius=.7071067811865476+Si.distanceTo(e.center),xi.applyMatrix4(e.matrixWorld),this.intersectsSphere(xi)}intersectsSphere(e){let t=this.planes,n=e.center,r=-e.radius;for(let e=0;e<6;e++)if(t[e].distanceToPoint(n)<r)return!1;return!0}intersectsBox(e){let t=this.planes;for(let n=0;n<6;n++){let r=t[n];if(Ci.x=r.normal.x>0?e.max.x:e.min.x,Ci.y=r.normal.y>0?e.max.y:e.min.y,Ci.z=r.normal.z>0?e.max.z:e.min.z,r.distanceToPoint(Ci)<0)return!1}return!0}containsPoint(e){let t=this.planes;for(let n=0;n<6;n++)if(t[n].distanceToPoint(e)<0)return!1;return!0}clone(){return new this.constructor().copy(this)}},Ti=class extends Wr{constructor(e){super(),this.isLineBasicMaterial=!0,this.type=`LineBasicMaterial`,this.color=new Y(16777215),this.map=null,this.linewidth=1,this.linecap=`round`,this.linejoin=`round`,this.fog=!0,this.setValues(e)}copy(e){return super.copy(e),this.color.copy(e.color),this.map=e.map,this.linewidth=e.linewidth,this.linecap=e.linecap,this.linejoin=e.linejoin,this.fog=e.fog,this}},Ei=new G,Di=new G,Oi=new J,ki=new Yr,Ai=new Ar,ji=new G,Mi=new G,Ni=class extends Fn{constructor(e=new Rr,t=new Ti){super(),this.isLine=!0,this.type=`Line`,this.geometry=e,this.material=t,this.morphTargetDictionary=void 0,this.morphTargetInfluences=void 0,this.updateMorphTargets()}copy(e,t){return super.copy(e,t),this.material=Array.isArray(e.material)?e.material.slice():e.material,this.geometry=e.geometry,this}computeLineDistances(){let e=this.geometry;if(e.index===null){let t=e.attributes.position,n=[0];for(let e=1,r=t.count;e<r;e++)Ei.fromBufferAttribute(t,e-1),Di.fromBufferAttribute(t,e),n[e]=n[e-1],n[e]+=Ei.distanceTo(Di);e.setAttribute(`lineDistance`,new Er(n,1))}else V(`Line.computeLineDistances(): Computation only possible with non-indexed BufferGeometry.`);return this}intersectsFrustum(e){return e.intersectsObject(this)}raycast(e,t){let n=this.geometry,r=this.matrixWorld,i=e.params.Line.threshold,a=n.drawRange;if(n.boundingSphere===null&&n.computeBoundingSphere(),Ai.copy(n.boundingSphere),Ai.applyMatrix4(r),Ai.radius+=i,e.ray.intersectsSphere(Ai)===!1)return;Oi.copy(r).invert(),ki.copy(e.ray).applyMatrix4(Oi);let o=i/((this.scale.x+this.scale.y+this.scale.z)/3),s=o*o,c=this.isLineSegments?2:1,l=n.index,u=n.attributes.position;if(l!==null){let n=Math.max(0,a.start),r=Math.min(l.count,a.start+a.count);for(let i=n,a=r-1;i<a;i+=c){let n=l.getX(i),r=l.getX(i+1),a=Pi(this,e,ki,s,n,r,i);a&&t.push(a)}if(this.isLineLoop){let i=l.getX(r-1),a=l.getX(n),o=Pi(this,e,ki,s,i,a,r-1);o&&t.push(o)}}else{let n=Math.max(0,a.start),r=Math.min(u.count,a.start+a.count);for(let i=n,a=r-1;i<a;i+=c){let n=Pi(this,e,ki,s,i,i+1,i);n&&t.push(n)}if(this.isLineLoop){let i=Pi(this,e,ki,s,r-1,n,r-1);i&&t.push(i)}}}updateMorphTargets(){let e=this.geometry.morphAttributes,t=Object.keys(e);if(t.length>0){let n=e[t[0]];if(n!==void 0){this.morphTargetInfluences=[],this.morphTargetDictionary={};for(let e=0,t=n.length;e<t;e++){let t=n[e].name||String(e);this.morphTargetInfluences.push(0),this.morphTargetDictionary[t]=e}}}}};function Pi(e,t,n,r,i,a,o){let s=e.geometry.attributes.position;if(Ei.fromBufferAttribute(s,i),Di.fromBufferAttribute(s,a),n.distanceSqToSegment(Ei,Di,ji,Mi)>r)return;ji.applyMatrix4(e.matrixWorld);let c=t.ray.origin.distanceTo(ji);if(!(c<t.near||c>t.far))return{distance:c,point:Mi.clone().applyMatrix4(e.matrixWorld),index:o,face:null,faceIndex:null,barycoord:null,object:e}}var Fi=new G,Ii=new G,Li=class extends Ni{constructor(e,t){super(e,t),this.isLineSegments=!0,this.type=`LineSegments`}computeLineDistances(){let e=this.geometry;if(e.index===null){let t=e.attributes.position,n=[];for(let e=0,r=t.count;e<r;e+=2)Fi.fromBufferAttribute(t,e),Ii.fromBufferAttribute(t,e+1),n[e]=e===0?0:n[e-1],n[e+1]=n[e]+Fi.distanceTo(Ii);e.setAttribute(`lineDistance`,new Er(n,1))}else V(`LineSegments.computeLineDistances(): Computation only possible with non-indexed BufferGeometry.`);return this}},Ri=class extends Wr{constructor(e){super(),this.isPointsMaterial=!0,this.type=`PointsMaterial`,this.color=new Y(16777215),this.map=null,this.alphaMap=null,this.size=1,this.sizeAttenuation=!0,this.fog=!0,this.setValues(e)}copy(e){return super.copy(e),this.color.copy(e.color),this.map=e.map,this.alphaMap=e.alphaMap,this.size=e.size,this.sizeAttenuation=e.sizeAttenuation,this.fog=e.fog,this}},zi=new J,Bi=new Yr,Vi=new Ar,Hi=new G,Ui=class extends Fn{constructor(e=new Rr,t=new Ri){super(),this.isPoints=!0,this.type=`Points`,this.geometry=e,this.material=t,this.morphTargetDictionary=void 0,this.morphTargetInfluences=void 0,this.updateMorphTargets()}copy(e,t){return super.copy(e,t),this.material=Array.isArray(e.material)?e.material.slice():e.material,this.geometry=e.geometry,this}intersectsFrustum(e){return e.intersectsObject(this)}raycast(e,t){let n=this.geometry,r=this.matrixWorld,i=e.params.Points.threshold,a=n.drawRange;if(n.boundingSphere===null&&n.computeBoundingSphere(),Vi.copy(n.boundingSphere),Vi.applyMatrix4(r),Vi.radius+=i,e.ray.intersectsSphere(Vi)===!1)return;zi.copy(r).invert(),Bi.copy(e.ray).applyMatrix4(zi);let o=i/((this.scale.x+this.scale.y+this.scale.z)/3),s=o*o,c=n.index,l=n.attributes.position;if(c!==null){let n=Math.max(0,a.start),i=Math.min(c.count,a.start+a.count);for(let a=n,o=i;a<o;a++){let n=c.getX(a);Hi.fromBufferAttribute(l,n),Wi(Hi,n,s,r,e,t,this)}}else{let n=Math.max(0,a.start),i=Math.min(l.count,a.start+a.count);for(let a=n,o=i;a<o;a++)Hi.fromBufferAttribute(l,a),Wi(Hi,a,s,r,e,t,this)}}updateMorphTargets(){let e=this.geometry.morphAttributes,t=Object.keys(e);if(t.length>0){let n=e[t[0]];if(n!==void 0){this.morphTargetInfluences=[],this.morphTargetDictionary={};for(let e=0,t=n.length;e<t;e++){let t=n[e].name||String(e);this.morphTargetInfluences.push(0),this.morphTargetDictionary[t]=e}}}}};function Wi(e,t,n,r,i,a,o){let s=Bi.distanceSqToPoint(e);if(s<n){let n=new G;Bi.closestPointToPoint(e,n),n.applyMatrix4(r);let c=i.ray.origin.distanceTo(n);if(c<i.near||c>i.far)return;a.push({distance:c,distanceToRay:Math.sqrt(s),point:n,index:t,face:null,faceIndex:null,barycoord:null,object:o})}}var Gi=class extends nn{constructor(e=[],t=301,n,r,i,a,o,s,c,l){super(e,t,n,r,i,a,o,s,c,l),this.isCubeTexture=!0,this.flipY=!1}get images(){return this.image}set images(e){this.image=e}},Ki=class extends nn{constructor(e,t,n,r,i,a,o,s,c){super(e,t,n,r,i,a,o,s,c),this.isCanvasTexture=!0,this.needsUpdate=!0}},qi=class extends nn{constructor(e,t,n=k,r,i,a,o=v,s=v,c,l=oe,u=1){if(l!==1026&&l!==1027)throw Error(`THREE.DepthTexture: format must be either THREE.DepthFormat or THREE.DepthStencilFormat`);super({width:e,height:t,depth:u},r,i,a,o,s,l,n,c),this.isDepthTexture=!0,this.flipY=!1,this.generateMipmaps=!1,this.compareFunction=null}copy(e){return super.copy(e),this.source=new Qt(Object.assign({},e.image)),this.compareFunction=e.compareFunction,this}toJSON(e){let t=super.toJSON(e);return t.compareFunction=this.compareFunction,t}},Ji=class extends qi{constructor(e,t=k,n=301,r,i,a=v,o=v,s,c=oe){let l={width:e,height:e,depth:1},u=[l,l,l,l,l,l];super(e,e,t,n,r,i,a,o,s,c),this.image=u,this.isCubeDepthTexture=!0,this.isCubeTexture=!0}get images(){return this.image}set images(e){this.image=e}},Yi=class extends nn{constructor(e=null){super(),this.sourceTexture=e,this.isExternalTexture=!0}copy(e){return super.copy(e),this.sourceTexture=e.sourceTexture,this}},Xi=class e extends Rr{constructor(e=1,t=1,n=1,r=1,i=1,a=1){super(),this.type=`BoxGeometry`,this.parameters={width:e,height:t,depth:n,widthSegments:r,heightSegments:i,depthSegments:a};let o=this;r=Math.floor(r),i=Math.floor(i),a=Math.floor(a);let s=[],c=[],l=[],u=[],d=0,f=0;p(`z`,`y`,`x`,-1,-1,n,t,e,a,i,0),p(`z`,`y`,`x`,1,-1,n,t,-e,a,i,1),p(`x`,`z`,`y`,1,1,e,n,t,r,a,2),p(`x`,`z`,`y`,1,-1,e,n,-t,r,a,3),p(`x`,`y`,`z`,1,-1,e,t,n,r,i,4),p(`x`,`y`,`z`,-1,-1,e,t,-n,r,i,5),this.setIndex(s),this.setAttribute(`position`,new Er(c,3)),this.setAttribute(`normal`,new Er(l,3)),this.setAttribute(`uv`,new Er(u,2));function p(e,t,n,r,i,a,p,m,h,g,_){let v=a/h,y=p/g,b=a/2,x=p/2,S=m/2,C=h+1,w=g+1,T=0,E=0,D=new G;for(let a=0;a<w;a++){let o=a*y-x;for(let s=0;s<C;s++)D[e]=(s*v-b)*r,D[t]=o*i,D[n]=S,c.push(D.x,D.y,D.z),D[e]=0,D[t]=0,D[n]=m>0?1:-1,l.push(D.x,D.y,D.z),u.push(s/h),u.push(1-a/g),T+=1}for(let e=0;e<g;e++)for(let t=0;t<h;t++){let n=d+t+C*e,r=d+t+C*(e+1),i=d+(t+1)+C*(e+1),a=d+(t+1)+C*e;s.push(n,r,a),s.push(r,i,a),E+=6}o.addGroup(f,E,_),f+=E,d+=T}}copy(e){return super.copy(e),this.parameters=Object.assign({},e.parameters),this}static fromJSON(t){return new e(t.width,t.height,t.depth,t.widthSegments,t.heightSegments,t.depthSegments)}},Zi=new G,Qi=new G,$i=new G,ea=new ar,ta=class extends Rr{constructor(e=null,t=1){if(super(),this.type=`EdgesGeometry`,this.parameters={geometry:e,thresholdAngle:t},e!==null){let n=1e4,r=Math.cos(gt*t),i=e.getIndex(),a=e.getAttribute(`position`),o=i?i.count:a.count,s=[0,0,0],c=[`a`,`b`,`c`],l=[,,,],u={},d=[];for(let e=0;e<o;e+=3){i?(s[0]=i.getX(e),s[1]=i.getX(e+1),s[2]=i.getX(e+2)):(s[0]=e,s[1]=e+1,s[2]=e+2);let{a:t,b:o,c:f}=ea;if(t.fromBufferAttribute(a,s[0]),o.fromBufferAttribute(a,s[1]),f.fromBufferAttribute(a,s[2]),ea.getNormal($i),l[0]=`${Math.round(t.x*n)},${Math.round(t.y*n)},${Math.round(t.z*n)}`,l[1]=`${Math.round(o.x*n)},${Math.round(o.y*n)},${Math.round(o.z*n)}`,l[2]=`${Math.round(f.x*n)},${Math.round(f.y*n)},${Math.round(f.z*n)}`,l[0]!==l[1]&&l[1]!==l[2]&&l[2]!==l[0])for(let e=0;e<3;e++){let t=(e+1)%3,n=l[e],i=l[t],a=ea[c[e]],o=ea[c[t]],f=`${n}_${i}`,p=`${i}_${n}`;p in u&&u[p]?($i.dot(u[p].normal)<=r&&(d.push(a.x,a.y,a.z),d.push(o.x,o.y,o.z)),u[p]=null):f in u||(u[f]={index0:s[e],index1:s[t],normal:$i.clone()})}}for(let e in u)if(u[e]){let{index0:t,index1:n}=u[e];Zi.fromBufferAttribute(a,t),Qi.fromBufferAttribute(a,n),d.push(Zi.x,Zi.y,Zi.z),d.push(Qi.x,Qi.y,Qi.z)}this.setAttribute(`position`,new Er(d,3))}}copy(e){return super.copy(e),this.parameters=Object.assign({},e.parameters),this}},na=class e extends Rr{constructor(e=1,t=1,n=1,r=1){super(),this.type=`PlaneGeometry`,this.parameters={width:e,height:t,widthSegments:n,heightSegments:r};let i=e/2,a=t/2,o=Math.floor(n),s=Math.floor(r),c=o+1,l=s+1,u=e/o,d=t/s,f=[],p=[],m=[],h=[];for(let e=0;e<l;e++){let t=e*d-a;for(let n=0;n<c;n++){let r=n*u-i;p.push(r,-t,0),m.push(0,0,1),h.push(n/o),h.push(1-e/s)}}for(let e=0;e<s;e++)for(let t=0;t<o;t++){let n=t+c*e,r=t+c*(e+1),i=t+1+c*(e+1),a=t+1+c*e;f.push(n,r,a),f.push(r,i,a)}this.setIndex(f),this.setAttribute(`position`,new Er(p,3)),this.setAttribute(`normal`,new Er(m,3)),this.setAttribute(`uv`,new Er(h,2))}copy(e){return super.copy(e),this.parameters=Object.assign({},e.parameters),this}static fromJSON(t){return new e(t.width,t.height,t.widthSegments,t.heightSegments)}},ra=class e extends Rr{constructor(e=1,t=32,n=16,r=0,i=Math.PI*2,a=0,o=Math.PI){super(),this.type=`SphereGeometry`,this.parameters={radius:e,widthSegments:t,heightSegments:n,phiStart:r,phiLength:i,thetaStart:a,thetaLength:o},t=Math.max(3,Math.floor(t)),n=Math.max(2,Math.floor(n));let s=Math.min(a+o,Math.PI),c=0,l=[],u=new G,d=new G,f=[],p=[],m=[],h=[];for(let f=0;f<=n;f++){let g=[],_=f/n,v=a+_*o,y=e*Math.cos(v),b=Math.sqrt(e*e-y*y),x=0;f===0&&a===0?x=.5/t:f===n&&s===Math.PI&&(x=-.5/t);for(let e=0;e<=t;e++){let n=e/t,a=r+n*i;u.x=-b*Math.cos(a),u.y=y,u.z=b*Math.sin(a),p.push(u.x,u.y,u.z),d.copy(u).normalize(),m.push(d.x,d.y,d.z),h.push(n+x,1-_),g.push(c++)}l.push(g)}for(let e=0;e<n;e++)for(let r=0;r<t;r++){let t=l[e][r+1],i=l[e][r],o=l[e+1][r],c=l[e+1][r+1];(e!==0||a>0)&&f.push(t,i,c),(e!==n-1||s<Math.PI)&&f.push(i,o,c)}this.setIndex(f),this.setAttribute(`position`,new Er(p,3)),this.setAttribute(`normal`,new Er(m,3)),this.setAttribute(`uv`,new Er(h,2))}copy(e){return super.copy(e),this.parameters=Object.assign({},e.parameters),this}static fromJSON(t){return new e(t.radius,t.widthSegments,t.heightSegments,t.phiStart,t.phiLength,t.thetaStart,t.thetaLength)}};function ia(e){let t={};for(let n in e){t[n]={};for(let r in e[n]){let i=e[n][r];if(oa(i))i.isRenderTargetTexture?(V(`UniformsUtils: Textures of render targets cannot be cloned via cloneUniforms() or mergeUniforms().`),t[n][r]=null):t[n][r]=i.clone();else if(Array.isArray(i)){if(oa(i[0])){let e=[];for(let t=0,n=i.length;t<n;t++)e[t]=i[t].clone();t[n][r]=e}else t[n][r]=i.slice()}else t[n][r]=i}}return t}function aa(e){let t={};for(let n=0;n<e.length;n++){let r=ia(e[n]);for(let e in r)t[e]=r[e]}return t}function oa(e){return e&&(e.isColor||e.isMatrix3||e.isMatrix4||e.isVector2||e.isVector3||e.isVector4||e.isTexture||e.isQuaternion)}function sa(e){let t=[];for(let n=0;n<e.length;n++)t.push(e[n].clone());return t}function ca(e){let t=e.getRenderTarget();return t===null?e.outputColorSpace:t.isXRRenderTarget===!0?t.texture.colorSpace:q.workingColorSpace}var la={clone:ia,merge:aa},ua=`void main() {
	gl_Position = projectionMatrix * modelViewMatrix * vec4( position, 1.0 );
}`,da=`void main() {
	gl_FragColor = vec4( 1.0, 0.0, 0.0, 1.0 );
}`,fa=class extends Wr{constructor(e){super(),this.isShaderMaterial=!0,this.type=`ShaderMaterial`,this.defines={},this.uniforms={},this.uniformsGroups=[],this.vertexShader=ua,this.fragmentShader=da,this.linewidth=1,this.wireframe=!1,this.wireframeLinewidth=1,this.fog=!1,this.lights=!1,this.clipping=!1,this.forceSinglePass=!0,this.extensions={clipCullDistance:!1,multiDraw:!1},this.defaultAttributeValues={color:[1,1,1],uv:[0,0],uv1:[0,0]},this.index0AttributeName=void 0,this.uniformsNeedUpdate=!1,this.glslVersion=null,e!==void 0&&this.setValues(e)}copy(e){return super.copy(e),this.fragmentShader=e.fragmentShader,this.vertexShader=e.vertexShader,this.uniforms=ia(e.uniforms),this.uniformsGroups=sa(e.uniformsGroups),this.defines=Object.assign({},e.defines),this.wireframe=e.wireframe,this.wireframeLinewidth=e.wireframeLinewidth,this.fog=e.fog,this.lights=e.lights,this.clipping=e.clipping,this.extensions=Object.assign({},e.extensions),this.glslVersion=e.glslVersion,this.defaultAttributeValues=Object.assign({},e.defaultAttributeValues),this.index0AttributeName=e.index0AttributeName,this.uniformsNeedUpdate=e.uniformsNeedUpdate,this}toJSON(e){let t=super.toJSON(e);t.glslVersion=this.glslVersion,t.uniforms={};for(let n in this.uniforms){let r=this.uniforms[n].value;r&&r.isTexture?t.uniforms[n]={type:`t`,value:r.toJSON(e).uuid}:r&&r.isColor?t.uniforms[n]={type:`c`,value:r.getHex()}:r&&r.isVector2?t.uniforms[n]={type:`v2`,value:r.toArray()}:r&&r.isVector3?t.uniforms[n]={type:`v3`,value:r.toArray()}:r&&r.isVector4?t.uniforms[n]={type:`v4`,value:r.toArray()}:r&&r.isMatrix3?t.uniforms[n]={type:`m3`,value:r.toArray()}:r&&r.isMatrix4?t.uniforms[n]={type:`m4`,value:r.toArray()}:t.uniforms[n]={value:r}}Object.keys(this.defines).length>0&&(t.defines=this.defines),t.vertexShader=this.vertexShader,t.fragmentShader=this.fragmentShader,t.lights=this.lights,t.clipping=this.clipping;let n={};for(let e in this.extensions)this.extensions[e]===!0&&(n[e]=!0);return Object.keys(n).length>0&&(t.extensions=n),t}fromJSON(e,t){if(super.fromJSON(e,t),e.uniforms!==void 0)for(let n in e.uniforms){let r=e.uniforms[n];switch(this.uniforms[n]={},r.type){case`t`:this.uniforms[n].value=t[r.value]||null;break;case`c`:this.uniforms[n].value=new Y().setHex(r.value);break;case`v2`:this.uniforms[n].value=new W().fromArray(r.value);break;case`v3`:this.uniforms[n].value=new G().fromArray(r.value);break;case`v4`:this.uniforms[n].value=new rn().fromArray(r.value);break;case`m3`:this.uniforms[n].value=new K().fromArray(r.value);break;case`m4`:this.uniforms[n].value=new J().fromArray(r.value);break;default:this.uniforms[n].value=r.value}}if(e.defines!==void 0&&(this.defines=e.defines),e.vertexShader!==void 0&&(this.vertexShader=e.vertexShader),e.fragmentShader!==void 0&&(this.fragmentShader=e.fragmentShader),e.glslVersion!==void 0&&(this.glslVersion=e.glslVersion),e.extensions!==void 0)for(let t in e.extensions)this.extensions[t]=e.extensions[t];return e.lights!==void 0&&(this.lights=e.lights),e.clipping!==void 0&&(this.clipping=e.clipping),this}},pa=class extends fa{constructor(e){super(e),this.isRawShaderMaterial=!0,this.type=`RawShaderMaterial`}},ma=class extends Wr{constructor(e){super(),this.isMeshDepthMaterial=!0,this.type=`MeshDepthMaterial`,this.depthPacking=Ye,this.map=null,this.alphaMap=null,this.displacementMap=null,this.displacementScale=1,this.displacementBias=0,this.wireframe=!1,this.wireframeLinewidth=1,this.setValues(e)}copy(e){return super.copy(e),this.depthPacking=e.depthPacking,this.map=e.map,this.alphaMap=e.alphaMap,this.displacementMap=e.displacementMap,this.displacementScale=e.displacementScale,this.displacementBias=e.displacementBias,this.wireframe=e.wireframe,this.wireframeLinewidth=e.wireframeLinewidth,this}},ha=class extends Wr{constructor(e){super(),this.isMeshDistanceMaterial=!0,this.type=`MeshDistanceMaterial`,this.map=null,this.alphaMap=null,this.displacementMap=null,this.displacementScale=1,this.displacementBias=0,this.setValues(e)}copy(e){return super.copy(e),this.map=e.map,this.alphaMap=e.alphaMap,this.displacementMap=e.displacementMap,this.displacementScale=e.displacementScale,this.displacementBias=e.displacementBias,this}},ga=class extends Ti{constructor(e){super(),this.isLineDashedMaterial=!0,this.type=`LineDashedMaterial`,this.scale=1,this.dashSize=3,this.gapSize=1,this.setValues(e)}copy(e){return super.copy(e),this.scale=e.scale,this.dashSize=e.dashSize,this.gapSize=e.gapSize,this}};function _a(e,t){return!e||e.constructor===t?e:typeof t.BYTES_PER_ELEMENT==`number`?new t(e):Array.prototype.slice.call(e)}function va(e){return e!==void 0&&e.inTangents!==void 0&&e.outTangents!==void 0}var ya=class{constructor(e,t,n,r){this.parameterPositions=e,this._cachedIndex=0,this.resultBuffer=r===void 0?new t.constructor(n):r,this.sampleValues=t,this.valueSize=n,this.settings=null,this.DefaultSettings_={}}evaluate(e){let t=this.parameterPositions,n=this._cachedIndex,r=t[n],i=t[n-1];validate_interval:{seek:{let a;linear_scan:{forward_scan:if(!(e<r)){for(let a=n+2;;){if(r===void 0){if(e<i)break forward_scan;return n=t.length,this._cachedIndex=n,this.copySampleValue_(n-1)}if(n===a)break;if(i=r,r=t[++n],e<r)break seek}a=t.length;break linear_scan}if(!(e>=i)){let o=t[1];e<o&&(n=2,i=o);for(let a=n-2;;){if(i===void 0)return this._cachedIndex=0,this.copySampleValue_(0);if(n===a)break;if(r=i,i=t[--n-1],e>=i)break seek}a=n,n=0;break linear_scan}break validate_interval}for(;n<a;){let r=n+a>>>1;e<t[r]?a=r:n=r+1}if(r=t[n],i=t[n-1],i===void 0)return this._cachedIndex=0,this.copySampleValue_(0);if(r===void 0)return n=t.length,this._cachedIndex=n,this.copySampleValue_(n-1)}this._cachedIndex=n,this.intervalChanged_(n,i,r)}return this.interpolate_(n,i,e,r)}getSettings_(){return this.settings||this.DefaultSettings_}copySampleValue_(e){let t=this.resultBuffer,n=this.sampleValues,r=this.valueSize,i=e*r;for(let e=0;e!==r;++e)t[e]=n[i+e];return t}interpolate_(){throw Error(`THREE.Interpolant: Call to abstract method.`)}intervalChanged_(){}},ba=class extends ya{constructor(e,t,n,r){super(e,t,n,r),this._weightPrev=-0,this._offsetPrev=-0,this._weightNext=-0,this._offsetNext=-0,this.DefaultSettings_={endingStart:Ke,endingEnd:Ke}}intervalChanged_(e,t,n){let r=this.parameterPositions,i=e-2,a=e+1,o=r[i],s=r[a];if(o===void 0)switch(this.getSettings_().endingStart){case qe:i=e,o=2*t-n;break;case Je:i=r.length-2,o=t+r[i]-r[i+1];break;default:i=e,o=n}if(s===void 0)switch(this.getSettings_().endingEnd){case qe:a=e,s=2*n-t;break;case Je:a=1,s=n+r[1]-r[0];break;default:a=e-1,s=t}let c=(n-t)*.5,l=this.valueSize;this._weightPrev=c/(t-o),this._weightNext=c/(s-n),this._offsetPrev=i*l,this._offsetNext=a*l}interpolate_(e,t,n,r){let i=this.resultBuffer,a=this.sampleValues,o=this.valueSize,s=e*o,c=s-o,l=this._offsetPrev,u=this._offsetNext,d=this._weightPrev,f=this._weightNext,p=(n-t)/(r-t),m=p*p,h=m*p,g=-d*h+2*d*m-d*p,_=(1+d)*h+(-1.5-2*d)*m+(-.5+d)*p+1,v=(-1-f)*h+(1.5+f)*m+.5*p,y=f*h-f*m;for(let e=0;e!==o;++e)i[e]=g*a[l+e]+_*a[c+e]+v*a[s+e]+y*a[u+e];return i}},xa=class extends ya{constructor(e,t,n,r){super(e,t,n,r)}interpolate_(e,t,n,r){let i=this.resultBuffer,a=this.sampleValues,o=this.valueSize,s=e*o,c=s-o,l=(n-t)/(r-t),u=1-l;for(let e=0;e!==o;++e)i[e]=a[c+e]*u+a[s+e]*l;return i}},Sa=class extends ya{constructor(e,t,n,r){super(e,t,n,r)}interpolate_(e){return this.copySampleValue_(e-1)}},Ca=class extends ya{interpolate_(e,t,n,r){let i=this.resultBuffer,a=this.sampleValues,o=this.valueSize,s=e*o,c=s-o,l=this.inTangents,u=this.outTangents;if(!l||!u){let e=(n-t)/(r-t),l=1-e;for(let t=0;t!==o;++t)i[t]=a[c+t]*l+a[s+t]*e;return i}let d=o*2,f=e-1;for(let p=0;p!==o;++p){let o=a[c+p],m=a[s+p],h=f*d+p*2,g=u[h],_=u[h+1],v=e*d+p*2,y=l[v],b=l[v+1],x=Ea(n,t,g,y,r);i[p]=wa(x,o,_,b,m)}return i}};function wa(e,t,n,r,i){let a=1-e;return a*a*a*t+3*a*a*e*n+3*a*e*e*r+e*e*e*i}function Ta(e,t,n,r,i){let a=1-e;return 3*a*a*(n-t)+6*a*e*(r-n)+3*e*e*(i-r)}function Ea(e,t,n,r,i){let a=(e-t)/(i-t);for(let o=0;o<8;o++){let o=wa(a,t,n,r,i)-e;if(Math.abs(o)<1e-10)break;let s=Ta(a,t,n,r,i);if(Math.abs(s)<1e-10)break;a=Math.max(0,Math.min(1,a-o/s))}return a}var Da=class{constructor(e,t,n,r){if(e===void 0)throw Error(`THREE.KeyframeTrack: track name is undefined`);if(t===void 0||t.length===0)throw Error(`THREE.KeyframeTrack: no keyframes in track named `+e);this.name=e,this.times=_a(t,this.TimeBufferType),this.values=_a(n,this.ValueBufferType),this.setInterpolation(r||this.DefaultInterpolation)}static toJSON(e){let t=e.constructor,n;if(t.toJSON!==this.toJSON)n=t.toJSON(e);else{n={name:e.name,times:_a(e.times,Array),values:_a(e.values,Array)};let t=e.getInterpolation();t!==e.DefaultInterpolation&&(n.interpolation=t),va(e.settings)&&(n.settings={inTangents:_a(e.settings.inTangents,Array),outTangents:_a(e.settings.outTangents,Array)})}return n.type=e.ValueTypeName,n}InterpolantFactoryMethodDiscrete(e){return new Sa(this.times,this.values,this.getValueSize(),e)}InterpolantFactoryMethodLinear(e){return new xa(this.times,this.values,this.getValueSize(),e)}InterpolantFactoryMethodSmooth(e){return new ba(this.times,this.values,this.getValueSize(),e)}InterpolantFactoryMethodBezier(e){let t=new Ca(this.times,this.values,this.getValueSize(),e);return this.settings&&(t.inTangents=this.settings.inTangents,t.outTangents=this.settings.outTangents),t}setInterpolation(e){let t;switch(e){case He:t=this.InterpolantFactoryMethodDiscrete;break;case Ue:t=this.InterpolantFactoryMethodLinear;break;case We:t=this.InterpolantFactoryMethodSmooth;break;case Ge:t=this.InterpolantFactoryMethodBezier}if(t===void 0){let t=`unsupported interpolation for `+this.ValueTypeName+` keyframe track named `+this.name;if(this.createInterpolant===void 0){if(e!==this.DefaultInterpolation)this.setInterpolation(this.DefaultInterpolation);else throw Error(t)}return V(`KeyframeTrack:`,t),this}return this.createInterpolant=t,this}getInterpolation(){switch(this.createInterpolant){case this.InterpolantFactoryMethodDiscrete:return He;case this.InterpolantFactoryMethodLinear:return Ue;case this.InterpolantFactoryMethodSmooth:return We;case this.InterpolantFactoryMethodBezier:return Ge}}getValueSize(){return this.values.length/this.times.length}shift(e){if(e!==0){let t=this.times;for(let n=0,r=t.length;n!==r;++n)t[n]+=e}return this}scale(e){if(e!==1){let t=this.times;for(let n=0,r=t.length;n!==r;++n)t[n]*=e;va(this.settings)&&(Oa(this.settings.inTangents,e),Oa(this.settings.outTangents,e))}return this}trim(e,t){let n=this.times,r=n.length,i=0,a=r-1;for(;i!==r&&n[i]<e;)++i;for(;a!==-1&&n[a]>t;)--a;if(++a,i!==0||a!==r){i>=a&&(a=Math.max(a,1),i=a-1);let e=this.getValueSize();this.times=n.slice(i,a),this.values=this.values.slice(i*e,a*e)}return this}validate(){let e=!0,t=this.getValueSize();t-Math.floor(t)!==0&&(H(`KeyframeTrack: Invalid value size in track.`,this),e=!1);let n=this.times,r=this.values,i=n.length;i===0&&(H(`KeyframeTrack: Track is empty.`,this),e=!1);let a=null;for(let t=0;t!==i;t++){let r=n[t];if(typeof r==`number`&&isNaN(r)){H(`KeyframeTrack: Time is not a valid number.`,this,t,r),e=!1;break}if(a!==null&&a>r){H(`KeyframeTrack: Out of order keys.`,this,t,r,a),e=!1;break}a=r}if(r!==void 0&&it(r))for(let t=0,n=r.length;t!==n;++t){let n=r[t];if(isNaN(n)){H(`KeyframeTrack: Value is not a valid number.`,this,t,n),e=!1;break}}return e}optimize(){let e=this.times.slice(),t=this.values.slice(),n=this.getValueSize(),r=this.getInterpolation()===We,i=e.length-1,a=1;for(let o=1;o<i;++o){let i=!1,s=e[o];if(s!==e[o+1]&&(o!==1||s!==e[0])){if(r)i=!0;else{let e=o*n,r=e-n,a=e+n;for(let o=0;o!==n;++o){let n=t[e+o];if(n!==t[r+o]||n!==t[a+o]){i=!0;break}}}}if(i){if(o!==a){e[a]=e[o];let r=o*n,i=a*n;for(let e=0;e!==n;++e)t[i+e]=t[r+e]}++a}}if(i>0){e[a]=e[i];for(let e=i*n,r=a*n,o=0;o!==n;++o)t[r+o]=t[e+o];++a}return a===e.length?(this.times=e,this.values=t):(this.times=e.slice(0,a),this.values=t.slice(0,a*n)),this}clone(){let e=this.times.slice(),t=this.values.slice(),n=this.constructor,r=new n(this.name,e,t);return r.createInterpolant=this.createInterpolant,va(this.settings)&&(r.settings={inTangents:this.settings.inTangents.slice(),outTangents:this.settings.outTangents.slice()}),r}};function Oa(e,t){for(let n=0,r=e.length;n!==r;n+=2)e[n]*=t}Da.prototype.ValueTypeName=``,Da.prototype.TimeBufferType=Float32Array,Da.prototype.ValueBufferType=Float32Array,Da.prototype.DefaultInterpolation=Ue;var ka=class extends Da{constructor(e,t,n){super(e,t,n)}};ka.prototype.ValueTypeName=`bool`,ka.prototype.ValueBufferType=Array,ka.prototype.DefaultInterpolation=He,ka.prototype.InterpolantFactoryMethodLinear=void 0,ka.prototype.InterpolantFactoryMethodSmooth=void 0;var Aa=class extends Da{constructor(e,t,n,r){super(e,t,n,r)}};Aa.prototype.ValueTypeName=`color`;var ja=class extends Da{constructor(e,t,n,r){super(e,t,n,r)}};ja.prototype.ValueTypeName=`number`;var Ma=class extends ya{constructor(e,t,n,r){super(e,t,n,r)}interpolate_(e,t,n,r){let i=this.resultBuffer,a=this.sampleValues,o=this.valueSize,s=(n-t)/(r-t),c=e*o;for(let e=c+o;c!==e;c+=4)Bt.slerpFlat(i,0,a,c-o,a,c,s);return i}},Na=class extends Da{constructor(e,t,n,r){super(e,t,n,r)}InterpolantFactoryMethodLinear(e){return new Ma(this.times,this.values,this.getValueSize(),e)}};Na.prototype.ValueTypeName=`quaternion`,Na.prototype.InterpolantFactoryMethodSmooth=void 0;var Pa=class extends Da{constructor(e,t,n){super(e,t,n)}};Pa.prototype.ValueTypeName=`string`,Pa.prototype.ValueBufferType=Array,Pa.prototype.DefaultInterpolation=He,Pa.prototype.InterpolantFactoryMethodLinear=void 0,Pa.prototype.InterpolantFactoryMethodSmooth=void 0;var Fa=class extends Da{constructor(e,t,n,r){super(e,t,n,r)}};Fa.prototype.ValueTypeName=`vector`;var Ia=new G,La=new Bt,Ra=new G,za=class extends Fn{constructor(){super(),this.isCamera=!0,this.type=`Camera`,this.matrixWorldInverse=new J,this.projectionMatrix=new J,this.projectionMatrixInverse=new J,this.coordinateSystem=nt,this._reversedDepth=!1}get reversedDepth(){return this._reversedDepth}copy(e,t){return super.copy(e,t),this.matrixWorldInverse.copy(e.matrixWorldInverse),this.projectionMatrix.copy(e.projectionMatrix),this.projectionMatrixInverse.copy(e.projectionMatrixInverse),this.coordinateSystem=e.coordinateSystem,this}getWorldDirection(e){return super.getWorldDirection(e).negate()}updateMatrixWorld(e){super.updateMatrixWorld(e),this.matrixWorld.decompose(Ia,La,Ra),Ra.x===1&&Ra.y===1&&Ra.z===1?this.matrixWorldInverse.copy(this.matrixWorld).invert():this.matrixWorldInverse.compose(Ia,La,Ra.set(1,1,1)).invert()}updateWorldMatrix(e,t,n=!1){super.updateWorldMatrix(e,t,n),this.matrixWorld.decompose(Ia,La,Ra),Ra.x===1&&Ra.y===1&&Ra.z===1?this.matrixWorldInverse.copy(this.matrixWorld).invert():this.matrixWorldInverse.compose(Ia,La,Ra.set(1,1,1)).invert()}clone(){return new this.constructor().copy(this)}},Ba=new G,Va=new W,Ha=new W,Ua=class extends za{constructor(e=50,t=1,n=.1,r=2e3){super(),this.isPerspectiveCamera=!0,this.type=`PerspectiveCamera`,this.fov=e,this.zoom=1,this.near=n,this.far=r,this.focus=10,this.aspect=t,this.view=null,this.filmGauge=35,this.filmOffset=0,this.updateProjectionMatrix()}copy(e,t){return super.copy(e,t),this.fov=e.fov,this.zoom=e.zoom,this.near=e.near,this.far=e.far,this.focus=e.focus,this.aspect=e.aspect,this.view=e.view===null?null:Object.assign({},e.view),this.filmGauge=e.filmGauge,this.filmOffset=e.filmOffset,this}setFocalLength(e){let t=.5*this.getFilmHeight()/e;this.fov=_t*2*Math.atan(t),this.updateProjectionMatrix()}getFocalLength(){let e=Math.tan(gt*.5*this.fov);return .5*this.getFilmHeight()/e}getEffectiveFOV(){return _t*2*Math.atan(Math.tan(gt*.5*this.fov)/this.zoom)}getFilmWidth(){return this.filmGauge*Math.min(this.aspect,1)}getFilmHeight(){return this.filmGauge/Math.max(this.aspect,1)}getViewBounds(e,t,n){Ba.set(-1,-1,.5).applyMatrix4(this.projectionMatrixInverse),t.set(Ba.x,Ba.y).multiplyScalar(-e/Ba.z),Ba.set(1,1,.5).applyMatrix4(this.projectionMatrixInverse),n.set(Ba.x,Ba.y).multiplyScalar(-e/Ba.z)}getViewSize(e,t){return this.getViewBounds(e,Va,Ha),t.subVectors(Ha,Va)}setViewOffset(e,t,n,r,i,a){this.aspect=e/t,this.view===null&&(this.view={enabled:!0,fullWidth:1,fullHeight:1,offsetX:0,offsetY:0,width:1,height:1}),this.view.enabled=!0,this.view.fullWidth=e,this.view.fullHeight=t,this.view.offsetX=n,this.view.offsetY=r,this.view.width=i,this.view.height=a,this.updateProjectionMatrix()}clearViewOffset(){this.view!==null&&(this.view.enabled=!1),this.updateProjectionMatrix()}updateProjectionMatrix(){let e=this.near,t=e*Math.tan(gt*.5*this.fov)/this.zoom,n=2*t,r=this.aspect*n,i=-.5*r,a=this.view;if(this.view!==null&&this.view.enabled){let e=a.fullWidth,o=a.fullHeight;i+=a.offsetX*r/e,t-=a.offsetY*n/o,r*=a.width/e,n*=a.height/o}let o=this.filmOffset;o!==0&&(i+=e*o/this.getFilmWidth()),this.projectionMatrix.makePerspective(i,i+r,t,t-n,e,this.far,this.coordinateSystem,this.reversedDepth),this.projectionMatrixInverse.copy(this.projectionMatrix).invert()}toJSON(e){let t=super.toJSON(e);return t.object.fov=this.fov,t.object.zoom=this.zoom,t.object.near=this.near,t.object.far=this.far,t.object.focus=this.focus,t.object.aspect=this.aspect,this.view!==null&&(t.object.view=Object.assign({},this.view)),t.object.filmGauge=this.filmGauge,t.object.filmOffset=this.filmOffset,t}},Wa=class extends za{constructor(e=-1,t=1,n=1,r=-1,i=.1,a=2e3){super(),this.isOrthographicCamera=!0,this.type=`OrthographicCamera`,this.zoom=1,this.view=null,this.left=e,this.right=t,this.top=n,this.bottom=r,this.near=i,this.far=a,this.updateProjectionMatrix()}copy(e,t){return super.copy(e,t),this.left=e.left,this.right=e.right,this.top=e.top,this.bottom=e.bottom,this.near=e.near,this.far=e.far,this.zoom=e.zoom,this.view=e.view===null?null:Object.assign({},e.view),this}setViewOffset(e,t,n,r,i,a){this.view===null&&(this.view={enabled:!0,fullWidth:1,fullHeight:1,offsetX:0,offsetY:0,width:1,height:1}),this.view.enabled=!0,this.view.fullWidth=e,this.view.fullHeight=t,this.view.offsetX=n,this.view.offsetY=r,this.view.width=i,this.view.height=a,this.updateProjectionMatrix()}clearViewOffset(){this.view!==null&&(this.view.enabled=!1),this.updateProjectionMatrix()}updateProjectionMatrix(){let e=(this.right-this.left)/(2*this.zoom),t=(this.top-this.bottom)/(2*this.zoom),n=(this.right+this.left)/2,r=(this.top+this.bottom)/2,i=n-e,a=n+e,o=r+t,s=r-t;if(this.view!==null&&this.view.enabled){let e=(this.right-this.left)/this.view.fullWidth/this.zoom,t=(this.top-this.bottom)/this.view.fullHeight/this.zoom;i+=e*this.view.offsetX,a=i+e*this.view.width,o-=t*this.view.offsetY,s=o-t*this.view.height}this.projectionMatrix.makeOrthographic(i,a,o,s,this.near,this.far,this.coordinateSystem,this.reversedDepth),this.projectionMatrixInverse.copy(this.projectionMatrix).invert()}toJSON(e){let t=super.toJSON(e);return t.object.zoom=this.zoom,t.object.left=this.left,t.object.right=this.right,t.object.top=this.top,t.object.bottom=this.bottom,t.object.near=this.near,t.object.far=this.far,this.view!==null&&(t.object.view=Object.assign({},this.view)),t}},Ga=-90,Ka=1,qa=class extends Fn{constructor(e,t,n){super(),this.type=`CubeCamera`,this.renderTarget=n,this.coordinateSystem=null,this.activeMipmapLevel=0;let r=new Ua(Ga,Ka,e,t);r.layers=this.layers,this.add(r);let i=new Ua(Ga,Ka,e,t);i.layers=this.layers,this.add(i);let a=new Ua(Ga,Ka,e,t);a.layers=this.layers,this.add(a);let o=new Ua(Ga,Ka,e,t);o.layers=this.layers,this.add(o);let s=new Ua(Ga,Ka,e,t);s.layers=this.layers,this.add(s);let c=new Ua(Ga,Ka,e,t);c.layers=this.layers,this.add(c)}updateCoordinateSystem(){let e=this.coordinateSystem,t=this.children.concat(),[n,r,i,a,o,s]=t;for(let e of t)this.remove(e);if(e===2e3)n.up.set(0,1,0),n.lookAt(1,0,0),r.up.set(0,1,0),r.lookAt(-1,0,0),i.up.set(0,0,-1),i.lookAt(0,1,0),a.up.set(0,0,1),a.lookAt(0,-1,0),o.up.set(0,1,0),o.lookAt(0,0,1),s.up.set(0,1,0),s.lookAt(0,0,-1);else if(e===2001)n.up.set(0,-1,0),n.lookAt(-1,0,0),r.up.set(0,-1,0),r.lookAt(1,0,0),i.up.set(0,0,1),i.lookAt(0,1,0),a.up.set(0,0,-1),a.lookAt(0,-1,0),o.up.set(0,-1,0),o.lookAt(0,0,1),s.up.set(0,-1,0),s.lookAt(0,0,-1);else throw Error(`THREE.CubeCamera.updateCoordinateSystem(): Invalid coordinate system: `+e);for(let e of t)this.add(e),e.updateMatrixWorld()}update(e,t){this.parent===null&&this.updateMatrixWorld();let{renderTarget:n,activeMipmapLevel:r}=this;this.coordinateSystem!==e.coordinateSystem&&(this.coordinateSystem=e.coordinateSystem,this.updateCoordinateSystem());let[i,a,o,s,c,l]=this.children,u=e.getRenderTarget(),d=e.getActiveCubeFace(),f=e.getActiveMipmapLevel(),p=e.xr.enabled;e.xr.enabled=!1;let m=n.texture.generateMipmaps;n.texture.generateMipmaps=!1;let h=!1;h=e.isWebGLRenderer===!0?e.state.buffers.depth.getReversed():e.reversedDepthBuffer,e.setRenderTarget(n,0,r),h&&e.autoClear===!1&&e.clearDepth(),e.render(t,i),e.setRenderTarget(n,1,r),h&&e.autoClear===!1&&e.clearDepth(),e.render(t,a),e.setRenderTarget(n,2,r),h&&e.autoClear===!1&&e.clearDepth(),e.render(t,o),e.setRenderTarget(n,3,r),h&&e.autoClear===!1&&e.clearDepth(),e.render(t,s),e.setRenderTarget(n,4,r),h&&e.autoClear===!1&&e.clearDepth(),e.render(t,c),n.texture.generateMipmaps=m,e.setRenderTarget(n,5,r),h&&e.autoClear===!1&&e.clearDepth(),e.render(t,l),e.setRenderTarget(u,d,f),e.xr.enabled=p,n.texture.needsPMREMUpdate=!0}},Ja=class extends Ua{constructor(e=[]){super(),this.isArrayCamera=!0,this.isMultiViewCamera=!1,this.cameras=e}},Ya=`\\[\\]\\.:\\/`,Xa=RegExp(`[\\[\\]\\.:\\/]`,`g`),Za=`[^\\[\\]\\.:\\/]`,Qa=`[^`+Ya.replace(`\\.`,``)+`]`,$a=`((?:WC+[\\/:])*)`.replace(`WC`,Za),eo=`(WCOD+)?`.replace(`WCOD`,Qa),to=`(?:\\.(WC+)(?:\\[(.+)\\])?)?`.replace(`WC`,Za),no=`\\.(WC+)(?:\\[(.+)\\])?`.replace(`WC`,Za),ro=RegExp(`^`+$a+eo+to+no+`$`),io=[`material`,`materials`,`bones`,`map`],ao=class{constructor(e,t,n){let r=n||oo.parseTrackName(t);this._targetGroup=e,this._bindings=e.subscribe_(t,r)}getValue(e,t){this.bind();let n=this._targetGroup.nCachedObjects_,r=this._bindings[n];r!==void 0&&r.getValue(e,t)}setValue(e,t){let n=this._bindings;for(let r=this._targetGroup.nCachedObjects_,i=n.length;r!==i;++r)n[r].setValue(e,t)}bind(){let e=this._bindings;for(let t=this._targetGroup.nCachedObjects_,n=e.length;t!==n;++t)e[t].bind()}unbind(){let e=this._bindings;for(let t=this._targetGroup.nCachedObjects_,n=e.length;t!==n;++t)e[t].unbind()}},oo=class e{constructor(t,n,r){this.path=n,this.parsedPath=r||e.parseTrackName(n),this.node=e.findNode(t,this.parsedPath.nodeName),this.rootNode=t,this.getValue=this._getValue_unbound,this.setValue=this._setValue_unbound}static create(t,n,r){return t&&t.isAnimationObjectGroup?new e.Composite(t,n,r):new e(t,n,r)}static sanitizeNodeName(e){return e.replace(/\s/g,`_`).replace(Xa,``)}static parseTrackName(e){let t=ro.exec(e);if(t===null)throw Error(`THREE.PropertyBinding: Cannot parse trackName: `+e);let n={nodeName:t[2],objectName:t[3],objectIndex:t[4],propertyName:t[5],propertyIndex:t[6]},r=n.nodeName&&n.nodeName.lastIndexOf(`.`);if(r!==void 0&&r!==-1){let e=n.nodeName.substring(r+1);io.indexOf(e)!==-1&&(n.nodeName=n.nodeName.substring(0,r),n.objectName=e)}if(n.propertyName===null||n.propertyName.length===0)throw Error(`THREE.PropertyBinding: can not parse propertyName from trackName: `+e);return n}static findNode(e,t){if(t===void 0||t===``||t===`.`||t===-1||t===e.name||t===e.uuid)return e;if(e.skeleton){let n=e.skeleton.getBoneByName(t);if(n!==void 0)return n}if(e.children){let n=function(e){for(let r=0;r<e.length;r++){let i=e[r];if(i.name===t||i.uuid===t)return i;let a=n(i.children);if(a)return a}return null},r=n(e.children);if(r)return r}return null}_getValue_unavailable(){}_setValue_unavailable(){}_getValue_direct(e,t){e[t]=this.targetObject[this.propertyName]}_getValue_array(e,t){let n=this.resolvedProperty;for(let r=0,i=n.length;r!==i;++r)e[t++]=n[r]}_getValue_arrayElement(e,t){e[t]=this.resolvedProperty[this.propertyIndex]}_getValue_toArray(e,t){this.resolvedProperty.toArray(e,t)}_setValue_direct(e,t){this.targetObject[this.propertyName]=e[t]}_setValue_direct_setNeedsUpdate(e,t){this.targetObject[this.propertyName]=e[t],this.targetObject.needsUpdate=!0}_setValue_direct_setMatrixWorldNeedsUpdate(e,t){this.targetObject[this.propertyName]=e[t],this.targetObject.matrixWorldNeedsUpdate=!0}_setValue_array(e,t){let n=this.resolvedProperty;for(let r=0,i=n.length;r!==i;++r)n[r]=e[t++]}_setValue_array_setNeedsUpdate(e,t){let n=this.resolvedProperty;for(let r=0,i=n.length;r!==i;++r)n[r]=e[t++];this.targetObject.needsUpdate=!0}_setValue_array_setMatrixWorldNeedsUpdate(e,t){let n=this.resolvedProperty;for(let r=0,i=n.length;r!==i;++r)n[r]=e[t++];this.targetObject.matrixWorldNeedsUpdate=!0}_setValue_arrayElement(e,t){this.resolvedProperty[this.propertyIndex]=e[t]}_setValue_arrayElement_setNeedsUpdate(e,t){this.resolvedProperty[this.propertyIndex]=e[t],this.targetObject.needsUpdate=!0}_setValue_arrayElement_setMatrixWorldNeedsUpdate(e,t){this.resolvedProperty[this.propertyIndex]=e[t],this.targetObject.matrixWorldNeedsUpdate=!0}_setValue_fromArray(e,t){this.resolvedProperty.fromArray(e,t)}_setValue_fromArray_setNeedsUpdate(e,t){this.resolvedProperty.fromArray(e,t),this.targetObject.needsUpdate=!0}_setValue_fromArray_setMatrixWorldNeedsUpdate(e,t){this.resolvedProperty.fromArray(e,t),this.targetObject.matrixWorldNeedsUpdate=!0}_getValue_unbound(e,t){this.bind(),this.getValue(e,t)}_setValue_unbound(e,t){this.bind(),this.setValue(e,t)}bind(){let t=this.node,n=this.parsedPath,r=n.objectName,i=n.propertyName,a=n.propertyIndex;if(t||(t=e.findNode(this.rootNode,n.nodeName),this.node=t),this.getValue=this._getValue_unavailable,this.setValue=this._setValue_unavailable,!t){V(`PropertyBinding: No target node found for track: `+this.path+`.`);return}if(r){let e=n.objectIndex;switch(r){case`materials`:if(!t.material){H(`PropertyBinding: Can not bind to material as node does not have a material.`,this);return}if(!t.material.materials){H(`PropertyBinding: Can not bind to material.materials as node.material does not have a materials array.`,this);return}t=t.material.materials;break;case`bones`:if(!t.skeleton){H(`PropertyBinding: Can not bind to bones as node does not have a skeleton.`,this);return}t=t.skeleton.bones;for(let n=0;n<t.length;n++)if(t[n].name===e){e=n;break}break;case`map`:if(`map`in t){t=t.map;break}if(!t.material){H(`PropertyBinding: Can not bind to material as node does not have a material.`,this);return}if(!t.material.map){H(`PropertyBinding: Can not bind to material.map as node.material does not have a map.`,this);return}t=t.material.map;break;default:if(t[r]===void 0){H(`PropertyBinding: Can not bind to objectName of node undefined.`,this);return}t=t[r]}if(e!==void 0){if(t[e]===void 0){H(`PropertyBinding: Trying to bind to objectIndex of objectName, but is undefined.`,this,t);return}t=t[e]}}let o=t[i];if(o===void 0){let e=n.nodeName;H(`PropertyBinding: Trying to update property for track: `+e+`.`+i+` but it wasn't found.`,t);return}let s=this.Versioning.None;this.targetObject=t,t.isMaterial===!0?s=this.Versioning.NeedsUpdate:t.isObject3D===!0&&(s=this.Versioning.MatrixWorldNeedsUpdate);let c=this.BindingType.Direct;if(a!==void 0){if(i===`morphTargetInfluences`){if(!t.geometry){H(`PropertyBinding: Can not bind to morphTargetInfluences because node does not have a geometry.`,this);return}if(!t.geometry.morphAttributes){H(`PropertyBinding: Can not bind to morphTargetInfluences because node does not have a geometry.morphAttributes.`,this);return}t.morphTargetDictionary[a]!==void 0&&(a=t.morphTargetDictionary[a])}c=this.BindingType.ArrayElement,this.resolvedProperty=o,this.propertyIndex=a}else o.fromArray!==void 0&&o.toArray!==void 0?(c=this.BindingType.HasFromToArray,this.resolvedProperty=o):Array.isArray(o)?(c=this.BindingType.EntireArray,this.resolvedProperty=o):this.propertyName=i;this.getValue=this.GetterByBindingType[c],this.setValue=this.SetterByBindingTypeAndVersioning[c][s]}unbind(){this.node=null,this.getValue=this._getValue_unbound,this.setValue=this._setValue_unbound}};oo.Composite=ao,oo.prototype.BindingType={Direct:0,EntireArray:1,ArrayElement:2,HasFromToArray:3},oo.prototype.Versioning={None:0,NeedsUpdate:1,MatrixWorldNeedsUpdate:2},oo.prototype.GetterByBindingType=[oo.prototype._getValue_direct,oo.prototype._getValue_array,oo.prototype._getValue_arrayElement,oo.prototype._getValue_toArray],oo.prototype.SetterByBindingTypeAndVersioning=[[oo.prototype._setValue_direct,oo.prototype._setValue_direct_setNeedsUpdate,oo.prototype._setValue_direct_setMatrixWorldNeedsUpdate],[oo.prototype._setValue_array,oo.prototype._setValue_array_setNeedsUpdate,oo.prototype._setValue_array_setMatrixWorldNeedsUpdate],[oo.prototype._setValue_arrayElement,oo.prototype._setValue_arrayElement_setNeedsUpdate,oo.prototype._setValue_arrayElement_setMatrixWorldNeedsUpdate],[oo.prototype._setValue_fromArray,oo.prototype._setValue_fromArray_setNeedsUpdate,oo.prototype._setValue_fromArray_setMatrixWorldNeedsUpdate]];var so=class{constructor(e=1,t=0,n=0){this.radius=e,this.phi=t,this.theta=n}set(e,t,n){return this.radius=e,this.phi=t,this.theta=n,this}copy(e){return this.radius=e.radius,this.phi=e.phi,this.theta=e.theta,this}makeSafe(){let e=1e-6;return this.phi=U(this.phi,e,Math.PI-e),this}setFromVector3(e){return this.setFromCartesianCoords(e.x,e.y,e.z)}setFromCartesianCoords(e,t,n){return this.radius=Math.sqrt(e*e+t*t+n*n),this.radius===0?(this.theta=0,this.phi=0):(this.theta=Math.atan2(e,n),this.phi=Math.acos(U(t/this.radius,-1,1))),this}clone(){return new this.constructor().copy(this)}};(class e{static{e.prototype.isMatrix2=!0}constructor(e,t,n,r){this.elements=[1,0,0,1],e!==void 0&&this.set(e,t,n,r)}identity(){return this.set(1,0,0,1),this}fromArray(e,t=0){for(let n=0;n<4;n++)this.elements[n]=e[n+t];return this}set(e,t,n,r){let i=this.elements;return i[0]=e,i[2]=t,i[1]=n,i[3]=r,this}});var co=class extends Li{constructor(e=10,t=10,n=4473924,r=8947848){n=new Y(n),r=new Y(r);let i=t/2,a=e/t,o=e/2,s=[],c=[];for(let e=0,l=0,u=-o;e<=t;e++,u+=a){s.push(-o,0,u,o,0,u),s.push(u,0,-o,u,0,o);let t=e===i?n:r;t.toArray(c,l),l+=3,t.toArray(c,l),l+=3,t.toArray(c,l),l+=3,t.toArray(c,l),l+=3}let l=new Rr;l.setAttribute(`position`,new Er(s,3)),l.setAttribute(`color`,new Er(c,3));let u=new Ti({vertexColors:!0,toneMapped:!1});super(l,u),this.type=`GridHelper`}dispose(){super.dispose(),this.geometry.dispose(),this.material.dispose()}},lo=class extends pt{constructor(e,t=null){super(),this.object=e,this.domElement=t,this.enabled=!0,this.state=-1,this.keys={},this.mouseButtons={LEFT:null,MIDDLE:null,RIGHT:null},this.touches={ONE:null,TWO:null}}connect(e){this.domElement!==null&&this.disconnect(),this.domElement=e}disconnect(){}dispose(){}update(){}};function uo(e,t,n,r){let i=fo(r);switch(n){case re:return e*t;case se:return e*t/i.components*i.byteLength;case ce:return e*t/i.components*i.byteLength;case F:return e*t*2/i.components*i.byteLength;case le:return e*t*2/i.components*i.byteLength;case ie:return e*t*3/i.components*i.byteLength;case ae:return e*t*4/i.components*i.byteLength;case ue:return e*t*4/i.components*i.byteLength;case de:case fe:return Math.floor((e+3)/4)*Math.floor((t+3)/4)*8;case pe:case me:return Math.floor((e+3)/4)*Math.floor((t+3)/4)*16;case ge:case ve:return Math.max(e,16)*Math.max(t,8)/4;case he:case _e:return Math.max(e,8)*Math.max(t,8)/2;case ye:case be:case Se:case Ce:return Math.floor((e+3)/4)*Math.floor((t+3)/4)*8;case xe:case we:case Te:return Math.floor((e+3)/4)*Math.floor((t+3)/4)*16;case Ee:return Math.floor((e+3)/4)*Math.floor((t+3)/4)*16;case De:return Math.floor((e+4)/5)*Math.floor((t+3)/4)*16;case Oe:return Math.floor((e+4)/5)*Math.floor((t+4)/5)*16;case ke:return Math.floor((e+5)/6)*Math.floor((t+4)/5)*16;case Ae:return Math.floor((e+5)/6)*Math.floor((t+5)/6)*16;case je:return Math.floor((e+7)/8)*Math.floor((t+4)/5)*16;case Me:return Math.floor((e+7)/8)*Math.floor((t+5)/6)*16;case I:return Math.floor((e+7)/8)*Math.floor((t+7)/8)*16;case Ne:return Math.floor((e+9)/10)*Math.floor((t+4)/5)*16;case L:return Math.floor((e+9)/10)*Math.floor((t+5)/6)*16;case Pe:return Math.floor((e+9)/10)*Math.floor((t+7)/8)*16;case R:return Math.floor((e+9)/10)*Math.floor((t+9)/10)*16;case Fe:return Math.floor((e+11)/12)*Math.floor((t+9)/10)*16;case z:return Math.floor((e+11)/12)*Math.floor((t+11)/12)*16;case B:case Ie:case Le:return Math.ceil(e/4)*Math.ceil(t/4)*16;case Re:case ze:return Math.ceil(e/4)*Math.ceil(t/4)*8;case Be:case Ve:return Math.ceil(e/4)*Math.ceil(t/4)*16}throw Error(`Unable to determine texture byte length for ${n} format.`)}function fo(e){switch(e){case w:case T:return{byteLength:1,components:1};case D:case E:case j:return{byteLength:2,components:1};case ee:case M:return{byteLength:2,components:4};case k:case O:case A:return{byteLength:4,components:1};case N:case ne:return{byteLength:4,components:3}}throw Error(`THREE.TextureUtils: Unknown texture type ${e}.`)}typeof __THREE_DEVTOOLS__<`u`&&__THREE_DEVTOOLS__.dispatchEvent(new CustomEvent(`register`,{detail:{revision:`186`}})),typeof window<`u`&&(window.__THREE__?V(`WARNING: Multiple instances of Three.js being imported.`):window.__THREE__=`186`);function po(){let e=null,t=!1,n=null,r=null;function i(t,a){r=e.requestAnimationFrame(i),n(t,a)}return{start:function(){t!==!0&&n!==null&&e!==null&&(r=e.requestAnimationFrame(i),t=!0)},stop:function(){e!==null&&e.cancelAnimationFrame(r),t=!1},setAnimationLoop:function(e){n=e},setContext:function(t){e=t}}}function mo(e){let t=new WeakMap;function n(t,n){let r=t.array,i=t.usage,a=r.byteLength,o=e.createBuffer();e.bindBuffer(n,o),e.bufferData(n,r,i),t.onUploadCallback();let s;if(r instanceof Float32Array)s=e.FLOAT;else if(typeof Float16Array<`u`&&r instanceof Float16Array)s=e.HALF_FLOAT;else if(r instanceof Uint16Array)s=t.isFloat16BufferAttribute?e.HALF_FLOAT:e.UNSIGNED_SHORT;else if(r instanceof Int16Array)s=e.SHORT;else if(r instanceof Uint32Array)s=e.UNSIGNED_INT;else if(r instanceof Int32Array)s=e.INT;else if(r instanceof Int8Array)s=e.BYTE;else if(r instanceof Uint8Array)s=e.UNSIGNED_BYTE;else if(r instanceof Uint8ClampedArray)s=e.UNSIGNED_BYTE;else throw Error(`THREE.WebGLAttributes: Unsupported buffer data format: `+r);return{buffer:o,type:s,bytesPerElement:r.BYTES_PER_ELEMENT,version:t.version,size:a}}function r(t,n,r){let i=n.array,a=n.updateRanges;if(e.bindBuffer(r,t),a.length===0)e.bufferSubData(r,0,i);else{a.sort((e,t)=>e.start-t.start);let t=0;for(let e=1;e<a.length;e++){let n=a[t],r=a[e];r.start<=n.start+n.count+1?n.count=Math.max(n.count,r.start+r.count-n.start):(++t,a[t]=r)}a.length=t+1;for(let t=0,n=a.length;t<n;t++){let n=a[t];e.bufferSubData(r,n.start*i.BYTES_PER_ELEMENT,i,n.start,n.count)}n.clearUpdateRanges()}n.onUploadCallback()}function i(e){return e.isInterleavedBufferAttribute&&(e=e.data),t.get(e)}function a(n){n.isInterleavedBufferAttribute&&(n=n.data);let r=t.get(n);r&&(e.deleteBuffer(r.buffer),t.delete(n))}function o(e,i){if(e.isInterleavedBufferAttribute&&(e=e.data),e.isGLBufferAttribute){let n=t.get(e);(!n||n.version<e.version)&&t.set(e,{buffer:e.buffer,type:e.type,bytesPerElement:e.elementSize,version:e.version});return}let a=t.get(e);if(a===void 0)t.set(e,n(e,i));else if(a.version<e.version){if(a.size!==e.array.byteLength)throw Error(`THREE.WebGLAttributes: The size of the buffer attribute's array buffer does not match the original size. Resizing buffer attributes is not supported.`);r(a.buffer,e,i),a.version=e.version}}return{get:i,remove:a,update:o}}var Z={alphahash_fragment:`#ifdef USE_ALPHAHASH
	if ( diffuseColor.a < getAlphaHashThreshold( vPosition ) ) discard;
#endif`,alphahash_pars_fragment:`#ifdef USE_ALPHAHASH
	const float ALPHA_HASH_SCALE = 0.05;
	float hash2D( vec2 value ) {
		return fract( 1.0e4 * sin( 17.0 * value.x + 0.1 * value.y ) * ( 0.1 + abs( sin( 13.0 * value.y + value.x ) ) ) );
	}
	float hash3D( vec3 value ) {
		return hash2D( vec2( hash2D( value.xy ), value.z ) );
	}
	float getAlphaHashThreshold( vec3 position ) {
		float maxDeriv = max(
			length( dFdx( position.xyz ) ),
			length( dFdy( position.xyz ) )
		);
		float pixScale = 1.0 / ( ALPHA_HASH_SCALE * maxDeriv );
		vec2 pixScales = vec2(
			exp2( floor( log2( pixScale ) ) ),
			exp2( ceil( log2( pixScale ) ) )
		);
		vec2 alpha = vec2(
			hash3D( floor( pixScales.x * position.xyz ) ),
			hash3D( floor( pixScales.y * position.xyz ) )
		);
		float lerpFactor = fract( log2( pixScale ) );
		float x = ( 1.0 - lerpFactor ) * alpha.x + lerpFactor * alpha.y;
		float a = min( lerpFactor, 1.0 - lerpFactor );
		vec3 cases = vec3(
			x * x / ( 2.0 * a * ( 1.0 - a ) ),
			( x - 0.5 * a ) / ( 1.0 - a ),
			1.0 - ( ( 1.0 - x ) * ( 1.0 - x ) / ( 2.0 * a * ( 1.0 - a ) ) )
		);
		float threshold = ( x < ( 1.0 - a ) )
			? ( ( x < a ) ? cases.x : cases.y )
			: cases.z;
		return clamp( threshold , 1.0e-6, 1.0 );
	}
#endif`,alphamap_fragment:`#ifdef USE_ALPHAMAP
	diffuseColor.a *= texture2D( alphaMap, vAlphaMapUv ).g;
#endif`,alphamap_pars_fragment:`#ifdef USE_ALPHAMAP
	uniform sampler2D alphaMap;
#endif`,alphatest_fragment:`#ifdef USE_ALPHATEST
	#ifdef ALPHA_TO_COVERAGE
	diffuseColor.a = smoothstep( alphaTest, alphaTest + fwidth( diffuseColor.a ), diffuseColor.a );
	if ( diffuseColor.a == 0.0 ) discard;
	#else
	if ( diffuseColor.a < alphaTest ) discard;
	#endif
#endif`,alphatest_pars_fragment:`#ifdef USE_ALPHATEST
	uniform float alphaTest;
#endif`,aomap_fragment:`#ifdef USE_AOMAP
	float ambientOcclusion = ( texture2D( aoMap, vAoMapUv ).r - 1.0 ) * aoMapIntensity + 1.0;
	reflectedLight.indirectDiffuse *= ambientOcclusion;
	#if defined( USE_CLEARCOAT ) 
		clearcoatSpecularIndirect *= ambientOcclusion;
	#endif
	#if defined( USE_SHEEN ) 
		sheenSpecularIndirect *= ambientOcclusion;
	#endif
	#if defined( USE_ENVMAP ) && defined( STANDARD )
		float dotNV = saturate( dot( geometryNormal, geometryViewDir ) );
		reflectedLight.indirectSpecular *= computeSpecularOcclusion( dotNV, ambientOcclusion, material.roughness );
	#endif
#endif`,aomap_pars_fragment:`#ifdef USE_AOMAP
	uniform sampler2D aoMap;
	uniform float aoMapIntensity;
#endif`,batching_pars_vertex:`#ifdef USE_BATCHING
	#if ! defined( GL_ANGLE_multi_draw )
	#define gl_DrawID _gl_DrawID
	uniform int _gl_DrawID;
	#endif
	uniform highp sampler2D batchingTexture;
	uniform highp usampler2D batchingIdTexture;
	mat4 getBatchingMatrix( const in float i ) {
		int size = textureSize( batchingTexture, 0 ).x;
		int j = int( i ) * 4;
		int x = j % size;
		int y = j / size;
		vec4 v1 = texelFetch( batchingTexture, ivec2( x, y ), 0 );
		vec4 v2 = texelFetch( batchingTexture, ivec2( x + 1, y ), 0 );
		vec4 v3 = texelFetch( batchingTexture, ivec2( x + 2, y ), 0 );
		vec4 v4 = texelFetch( batchingTexture, ivec2( x + 3, y ), 0 );
		return mat4( v1, v2, v3, v4 );
	}
	float getIndirectIndex( const in int i ) {
		int size = textureSize( batchingIdTexture, 0 ).x;
		int x = i % size;
		int y = i / size;
		return float( texelFetch( batchingIdTexture, ivec2( x, y ), 0 ).r );
	}
#endif
#ifdef USE_BATCHING_COLOR
	uniform sampler2D batchingColorTexture;
	vec4 getBatchingColor( const in float i ) {
		int size = textureSize( batchingColorTexture, 0 ).x;
		int j = int( i );
		int x = j % size;
		int y = j / size;
		return texelFetch( batchingColorTexture, ivec2( x, y ), 0 );
	}
#endif`,batching_vertex:`#ifdef USE_BATCHING
	mat4 batchingMatrix = getBatchingMatrix( getIndirectIndex( gl_DrawID ) );
#endif`,begin_vertex:`vec3 transformed = vec3( position );
#ifdef USE_ALPHAHASH
	vPosition = vec3( position );
#endif`,beginnormal_vertex:`vec3 objectNormal = vec3( normal );
#ifdef USE_TANGENT
	vec3 objectTangent = vec3( tangent.xyz );
#endif`,bsdfs:`float G_BlinnPhong_Implicit( ) {
	return 0.25;
}
float D_BlinnPhong( const in float shininess, const in float dotNH ) {
	return RECIPROCAL_PI * ( shininess * 0.5 + 1.0 ) * pow( dotNH, shininess );
}
vec3 BRDF_BlinnPhong( const in vec3 lightDir, const in vec3 viewDir, const in vec3 normal, const in vec3 specularColor, const in float shininess ) {
	vec3 halfDir = normalize( lightDir + viewDir );
	float dotNH = saturate( dot( normal, halfDir ) );
	float dotVH = saturate( dot( viewDir, halfDir ) );
	vec3 F = F_Schlick( specularColor, 1.0, dotVH );
	float G = G_BlinnPhong_Implicit( );
	float D = D_BlinnPhong( shininess, dotNH );
	return F * ( G * D );
} // validated`,iridescence_fragment:`#ifdef USE_IRIDESCENCE
	const mat3 XYZ_TO_REC709 = mat3(
		 3.2404542, -0.9692660,  0.0556434,
		-1.5371385,  1.8760108, -0.2040259,
		-0.4985314,  0.0415560,  1.0572252
	);
	vec3 Fresnel0ToIor( vec3 fresnel0 ) {
		vec3 sqrtF0 = sqrt( fresnel0 );
		return ( vec3( 1.0 ) + sqrtF0 ) / ( vec3( 1.0 ) - sqrtF0 );
	}
	vec3 IorToFresnel0( vec3 transmittedIor, float incidentIor ) {
		return pow2( ( transmittedIor - vec3( incidentIor ) ) / ( transmittedIor + vec3( incidentIor ) ) );
	}
	float IorToFresnel0( float transmittedIor, float incidentIor ) {
		return pow2( ( transmittedIor - incidentIor ) / ( transmittedIor + incidentIor ));
	}
	vec3 evalSensitivity( float OPD, vec3 shift ) {
		float phase = 2.0 * PI * OPD * 1.0e-9;
		vec3 val = vec3( 5.4856e-13, 4.4201e-13, 5.2481e-13 );
		vec3 pos = vec3( 1.6810e+06, 1.7953e+06, 2.2084e+06 );
		vec3 var = vec3( 4.3278e+09, 9.3046e+09, 6.6121e+09 );
		vec3 xyz = val * sqrt( 2.0 * PI * var ) * cos( pos * phase + shift ) * exp( - pow2( phase ) * var );
		xyz.x += 9.7470e-14 * sqrt( 2.0 * PI * 4.5282e+09 ) * cos( 2.2399e+06 * phase + shift[ 0 ] ) * exp( - 4.5282e+09 * pow2( phase ) );
		xyz /= 1.0685e-7;
		vec3 rgb = XYZ_TO_REC709 * xyz;
		return rgb;
	}
	vec3 evalIridescence( float outsideIOR, float eta2, float cosTheta1, float thinFilmThickness, vec3 baseF0 ) {
		vec3 I;
		float iridescenceIOR = mix( outsideIOR, eta2, smoothstep( 0.0, 0.03, thinFilmThickness ) );
		float sinTheta2Sq = pow2( outsideIOR / iridescenceIOR ) * ( 1.0 - pow2( cosTheta1 ) );
		float cosTheta2Sq = 1.0 - sinTheta2Sq;
		if ( cosTheta2Sq < 0.0 ) {
			return vec3( 1.0 );
		}
		float cosTheta2 = sqrt( cosTheta2Sq );
		float R0 = IorToFresnel0( iridescenceIOR, outsideIOR );
		float R12 = F_Schlick( R0, 1.0, cosTheta1 );
		float T121 = 1.0 - R12;
		float phi12 = 0.0;
		if ( iridescenceIOR < outsideIOR ) phi12 = PI;
		float phi21 = PI - phi12;
		vec3 baseIOR = Fresnel0ToIor( clamp( baseF0, 0.0, 0.9999 ) );		vec3 R1 = IorToFresnel0( baseIOR, iridescenceIOR );
		vec3 R23 = F_Schlick( R1, 1.0, cosTheta2 );
		vec3 phi23 = vec3( 0.0 );
		if ( baseIOR[ 0 ] < iridescenceIOR ) phi23[ 0 ] = PI;
		if ( baseIOR[ 1 ] < iridescenceIOR ) phi23[ 1 ] = PI;
		if ( baseIOR[ 2 ] < iridescenceIOR ) phi23[ 2 ] = PI;
		float OPD = 2.0 * iridescenceIOR * thinFilmThickness * cosTheta2;
		vec3 phi = vec3( phi21 ) + phi23;
		vec3 R123 = clamp( R12 * R23, 1e-5, 0.9999 );
		vec3 r123 = sqrt( R123 );
		vec3 Rs = pow2( T121 ) * R23 / ( vec3( 1.0 ) - R123 );
		vec3 C0 = R12 + Rs;
		I = C0;
		vec3 Cm = Rs - T121;
		for ( int m = 1; m <= 2; ++ m ) {
			Cm *= r123;
			vec3 Sm = 2.0 * evalSensitivity( float( m ) * OPD, float( m ) * phi );
			I += Cm * Sm;
		}
		return max( I, vec3( 0.0 ) );
	}
#endif`,bumpmap_pars_fragment:`#ifdef USE_BUMPMAP
	uniform sampler2D bumpMap;
	uniform float bumpScale;
	vec2 dHdxy_fwd() {
		vec2 dSTdx = dFdx( vBumpMapUv );
		vec2 dSTdy = dFdy( vBumpMapUv );
		float Hll = bumpScale * texture2D( bumpMap, vBumpMapUv ).x;
		float dBx = bumpScale * texture2D( bumpMap, vBumpMapUv + dSTdx ).x - Hll;
		float dBy = bumpScale * texture2D( bumpMap, vBumpMapUv + dSTdy ).x - Hll;
		return vec2( dBx, dBy );
	}
	vec3 perturbNormalArb( vec3 surf_pos, vec3 surf_norm, vec2 dHdxy, float faceDirection ) {
		vec3 vSigmaX = normalize( dFdx( surf_pos.xyz ) );
		vec3 vSigmaY = normalize( dFdy( surf_pos.xyz ) );
		vec3 vN = surf_norm;
		vec3 R1 = cross( vSigmaY, vN );
		vec3 R2 = cross( vN, vSigmaX );
		float fDet = dot( vSigmaX, R1 ) * faceDirection;
		vec3 vGrad = sign( fDet ) * ( dHdxy.x * R1 + dHdxy.y * R2 );
		return normalize( abs( fDet ) * surf_norm - vGrad );
	}
#endif`,clipping_planes_fragment:`#if NUM_CLIPPING_PLANES > 0
	vec4 plane;
	#ifdef ALPHA_TO_COVERAGE
		float distanceToPlane, distanceGradient;
		float clipOpacity = 1.0;
		#pragma unroll_loop_start
		for ( int i = 0; i < UNION_CLIPPING_PLANES; i ++ ) {
			plane = clippingPlanes[ i ];
			distanceToPlane = - dot( vClipPosition, plane.xyz ) + plane.w;
			distanceGradient = fwidth( distanceToPlane ) / 2.0;
			clipOpacity *= smoothstep( - distanceGradient, distanceGradient, distanceToPlane );
			if ( clipOpacity == 0.0 ) discard;
		}
		#pragma unroll_loop_end
		#if UNION_CLIPPING_PLANES < NUM_CLIPPING_PLANES
			float unionClipOpacity = 1.0;
			#pragma unroll_loop_start
			for ( int i = UNION_CLIPPING_PLANES; i < NUM_CLIPPING_PLANES; i ++ ) {
				plane = clippingPlanes[ i ];
				distanceToPlane = - dot( vClipPosition, plane.xyz ) + plane.w;
				distanceGradient = fwidth( distanceToPlane ) / 2.0;
				unionClipOpacity *= 1.0 - smoothstep( - distanceGradient, distanceGradient, distanceToPlane );
			}
			#pragma unroll_loop_end
			clipOpacity *= 1.0 - unionClipOpacity;
		#endif
		diffuseColor.a *= clipOpacity;
		if ( diffuseColor.a == 0.0 ) discard;
	#else
		#pragma unroll_loop_start
		for ( int i = 0; i < UNION_CLIPPING_PLANES; i ++ ) {
			plane = clippingPlanes[ i ];
			if ( dot( vClipPosition, plane.xyz ) > plane.w ) discard;
		}
		#pragma unroll_loop_end
		#if UNION_CLIPPING_PLANES < NUM_CLIPPING_PLANES
			bool clipped = true;
			#pragma unroll_loop_start
			for ( int i = UNION_CLIPPING_PLANES; i < NUM_CLIPPING_PLANES; i ++ ) {
				plane = clippingPlanes[ i ];
				clipped = ( dot( vClipPosition, plane.xyz ) > plane.w ) && clipped;
			}
			#pragma unroll_loop_end
			if ( clipped ) discard;
		#endif
	#endif
#endif`,clipping_planes_pars_fragment:`#if NUM_CLIPPING_PLANES > 0
	varying vec3 vClipPosition;
	uniform vec4 clippingPlanes[ NUM_CLIPPING_PLANES ];
#endif`,clipping_planes_pars_vertex:`#if NUM_CLIPPING_PLANES > 0
	varying vec3 vClipPosition;
#endif`,clipping_planes_vertex:`#if NUM_CLIPPING_PLANES > 0
	vClipPosition = - mvPosition.xyz;
#endif`,color_fragment:`#if defined( USE_COLOR ) || defined( USE_COLOR_ALPHA )
	diffuseColor *= vColor;
#endif`,color_pars_fragment:`#if defined( USE_COLOR ) || defined( USE_COLOR_ALPHA )
	varying vec4 vColor;
#endif`,color_pars_vertex:`#if defined( USE_COLOR ) || defined( USE_COLOR_ALPHA ) || defined( USE_INSTANCING_COLOR ) || defined( USE_BATCHING_COLOR )
	varying vec4 vColor;
#endif`,color_vertex:`#if defined( USE_COLOR ) || defined( USE_COLOR_ALPHA ) || defined( USE_INSTANCING_COLOR ) || defined( USE_BATCHING_COLOR )
	vColor = vec4( 1.0 );
#endif
#ifdef USE_COLOR_ALPHA
	vColor *= color;
#elif defined( USE_COLOR )
	vColor.rgb *= color;
#endif
#ifdef USE_INSTANCING_COLOR
	vColor.rgb *= instanceColor.rgb;
#endif
#ifdef USE_BATCHING_COLOR
	vColor *= getBatchingColor( getIndirectIndex( gl_DrawID ) );
#endif`,common:`#define PI 3.141592653589793
#define PI2 6.283185307179586
#define PI_HALF 1.5707963267948966
#define RECIPROCAL_PI 0.3183098861837907
#define RECIPROCAL_PI2 0.15915494309189535
#define EPSILON 1e-6
#ifndef saturate
#define saturate( a ) clamp( a, 0.0, 1.0 )
#endif
#define whiteComplement( a ) ( 1.0 - saturate( a ) )
float pow2( const in float x ) { return x*x; }
vec3 pow2( const in vec3 x ) { return x*x; }
float pow3( const in float x ) { return x*x*x; }
float pow4( const in float x ) { float x2 = x*x; return x2*x2; }
float max3( const in vec3 v ) { return max( max( v.x, v.y ), v.z ); }
float average( const in vec3 v ) { return dot( v, vec3( 0.3333333 ) ); }
highp float rand( const in vec2 uv ) {
	const highp float a = 12.9898, b = 78.233, c = 43758.5453;
	highp float dt = dot( uv.xy, vec2( a,b ) ), sn = mod( dt, PI );
	return fract( sin( sn ) * c );
}
#ifdef HIGH_PRECISION
	float precisionSafeLength( vec3 v ) { return length( v ); }
#else
	float precisionSafeLength( vec3 v ) {
		float maxComponent = max3( abs( v ) );
		return length( v / maxComponent ) * maxComponent;
	}
#endif
struct IncidentLight {
	vec3 color;
	vec3 direction;
	bool visible;
};
struct ReflectedLight {
	vec3 directDiffuse;
	vec3 directSpecular;
	vec3 indirectDiffuse;
	vec3 indirectSpecular;
};
#ifdef USE_ALPHAHASH
	varying vec3 vPosition;
#endif
vec3 transformDirection( in vec3 dir, in mat4 matrix ) {
	return normalize( ( matrix * vec4( dir, 0.0 ) ).xyz );
}
#define inverseTransformDirection transformDirectionByInverseViewMatrix
vec3 transformNormalByInverseViewMatrix( in vec3 normal, in mat4 viewMatrix ) {
	return normalize( ( vec4( normal, 0.0 ) * viewMatrix ).xyz );
}
vec3 transformDirectionByInverseViewMatrix( in vec3 dir, in mat4 viewMatrix ) {
	return normalize( ( vec4( dir, 0.0 ) * viewMatrix ).xyz );
}
bool isPerspectiveMatrix( mat4 m ) {
	return m[ 2 ][ 3 ] == - 1.0;
}
vec2 equirectUv( in vec3 dir ) {
	float u = atan( dir.z, dir.x ) * RECIPROCAL_PI2 + 0.5;
	float v = asin( clamp( dir.y, - 1.0, 1.0 ) ) * RECIPROCAL_PI + 0.5;
	return vec2( u, v );
}
vec3 BRDF_Lambert( const in vec3 diffuseColor ) {
	return RECIPROCAL_PI * diffuseColor;
}
vec3 F_Schlick( const in vec3 f0, const in float f90, const in float dotVH ) {
	float fresnel = exp2( ( - 5.55473 * dotVH - 6.98316 ) * dotVH );
	return f0 * ( 1.0 - fresnel ) + ( f90 * fresnel );
}
float F_Schlick( const in float f0, const in float f90, const in float dotVH ) {
	float fresnel = exp2( ( - 5.55473 * dotVH - 6.98316 ) * dotVH );
	return f0 * ( 1.0 - fresnel ) + ( f90 * fresnel );
} // validated`,cube_uv_reflection_fragment:`#ifdef ENVMAP_TYPE_CUBE_UV
	#define cubeUV_minMipLevel 4.0
	#define cubeUV_minTileSize 16.0
	float getFace( vec3 direction ) {
		vec3 absDirection = abs( direction );
		float face = - 1.0;
		if ( absDirection.x > absDirection.z ) {
			if ( absDirection.x > absDirection.y )
				face = direction.x > 0.0 ? 0.0 : 3.0;
			else
				face = direction.y > 0.0 ? 1.0 : 4.0;
		} else {
			if ( absDirection.z > absDirection.y )
				face = direction.z > 0.0 ? 2.0 : 5.0;
			else
				face = direction.y > 0.0 ? 1.0 : 4.0;
		}
		return face;
	}
	vec2 getUV( vec3 direction, float face ) {
		vec2 uv;
		if ( face == 0.0 ) {
			uv = vec2( direction.z, direction.y ) / abs( direction.x );
		} else if ( face == 1.0 ) {
			uv = vec2( - direction.x, - direction.z ) / abs( direction.y );
		} else if ( face == 2.0 ) {
			uv = vec2( - direction.x, direction.y ) / abs( direction.z );
		} else if ( face == 3.0 ) {
			uv = vec2( - direction.z, direction.y ) / abs( direction.x );
		} else if ( face == 4.0 ) {
			uv = vec2( - direction.x, direction.z ) / abs( direction.y );
		} else {
			uv = vec2( direction.x, direction.y ) / abs( direction.z );
		}
		return 0.5 * ( uv + 1.0 );
	}
	vec3 bilinearCubeUV( sampler2D envMap, vec3 direction, float mipInt ) {
		float face = getFace( direction );
		float filterInt = max( cubeUV_minMipLevel - mipInt, 0.0 );
		mipInt = max( mipInt, cubeUV_minMipLevel );
		float faceSize = exp2( mipInt );
		highp vec2 uv = getUV( direction, face ) * ( faceSize - 2.0 ) + 1.0;
		if ( face > 2.0 ) {
			uv.y += faceSize;
			face -= 3.0;
		}
		uv.x += face * faceSize;
		uv.x += filterInt * 3.0 * cubeUV_minTileSize;
		uv.y += 4.0 * ( exp2( CUBEUV_MAX_MIP ) - faceSize );
		uv.x *= CUBEUV_TEXEL_WIDTH;
		uv.y *= CUBEUV_TEXEL_HEIGHT;
		#ifdef texture2DGradEXT
			return texture2DGradEXT( envMap, uv, vec2( 0.0 ), vec2( 0.0 ) ).rgb;
		#else
			return texture2D( envMap, uv ).rgb;
		#endif
	}
	#define cubeUV_r0 1.0
	#define cubeUV_m0 - 2.0
	#define cubeUV_r1 0.8
	#define cubeUV_m1 - 1.0
	#define cubeUV_r4 0.4
	#define cubeUV_m4 2.0
	#define cubeUV_r5 0.305
	#define cubeUV_m5 3.0
	#define cubeUV_r6 0.21
	#define cubeUV_m6 4.0
	float roughnessToMip( float roughness ) {
		float mip = 0.0;
		if ( roughness >= cubeUV_r1 ) {
			mip = ( cubeUV_r0 - roughness ) * ( cubeUV_m1 - cubeUV_m0 ) / ( cubeUV_r0 - cubeUV_r1 ) + cubeUV_m0;
		} else if ( roughness >= cubeUV_r4 ) {
			mip = ( cubeUV_r1 - roughness ) * ( cubeUV_m4 - cubeUV_m1 ) / ( cubeUV_r1 - cubeUV_r4 ) + cubeUV_m1;
		} else if ( roughness >= cubeUV_r5 ) {
			mip = ( cubeUV_r4 - roughness ) * ( cubeUV_m5 - cubeUV_m4 ) / ( cubeUV_r4 - cubeUV_r5 ) + cubeUV_m4;
		} else if ( roughness >= cubeUV_r6 ) {
			mip = ( cubeUV_r5 - roughness ) * ( cubeUV_m6 - cubeUV_m5 ) / ( cubeUV_r5 - cubeUV_r6 ) + cubeUV_m5;
		} else {
			mip = - 2.0 * log2( 1.16 * roughness );		}
		return mip;
	}
	vec4 textureCubeUV( sampler2D envMap, vec3 sampleDir, float roughness ) {
		float mip = clamp( roughnessToMip( roughness ), cubeUV_m0, CUBEUV_MAX_MIP );
		float mipF = fract( mip );
		float mipInt = floor( mip );
		vec3 color0 = bilinearCubeUV( envMap, sampleDir, mipInt );
		if ( mipF == 0.0 ) {
			return vec4( color0, 1.0 );
		} else {
			vec3 color1 = bilinearCubeUV( envMap, sampleDir, mipInt + 1.0 );
			return vec4( mix( color0, color1, mipF ), 1.0 );
		}
	}
#endif`,defaultnormal_vertex:`vec3 transformedNormal = objectNormal;
#ifdef USE_TANGENT
	vec3 transformedTangent = objectTangent;
#endif
#ifdef USE_BATCHING
	mat3 bm = mat3( batchingMatrix );
	transformedNormal /= vec3( dot( bm[ 0 ], bm[ 0 ] ), dot( bm[ 1 ], bm[ 1 ] ), dot( bm[ 2 ], bm[ 2 ] ) );
	transformedNormal = bm * transformedNormal;
	#ifdef USE_TANGENT
		transformedTangent = bm * transformedTangent;
	#endif
#endif
#ifdef USE_INSTANCING
	mat3 im = mat3( instanceMatrix );
	transformedNormal /= vec3( dot( im[ 0 ], im[ 0 ] ), dot( im[ 1 ], im[ 1 ] ), dot( im[ 2 ], im[ 2 ] ) );
	transformedNormal = im * transformedNormal;
	#ifdef USE_TANGENT
		transformedTangent = im * transformedTangent;
	#endif
#endif
transformedNormal = normalMatrix * transformedNormal;
#ifdef FLIP_SIDED
	transformedNormal = - transformedNormal;
#endif
#ifdef USE_TANGENT
	transformedTangent = ( modelViewMatrix * vec4( transformedTangent, 0.0 ) ).xyz;
#endif`,displacementmap_pars_vertex:`#ifdef USE_DISPLACEMENTMAP
	uniform sampler2D displacementMap;
	uniform float displacementScale;
	uniform float displacementBias;
#endif`,displacementmap_vertex:`#ifdef USE_DISPLACEMENTMAP
	transformed += normalize( objectNormal ) * ( texture2D( displacementMap, vDisplacementMapUv ).x * displacementScale + displacementBias );
#endif`,emissivemap_fragment:`#ifdef USE_EMISSIVEMAP
	vec4 emissiveColor = texture2D( emissiveMap, vEmissiveMapUv );
	#ifdef DECODE_VIDEO_TEXTURE_EMISSIVE
		emissiveColor = sRGBTransferEOTF( emissiveColor );
	#endif
	totalEmissiveRadiance *= emissiveColor.rgb;
#endif`,emissivemap_pars_fragment:`#ifdef USE_EMISSIVEMAP
	uniform sampler2D emissiveMap;
#endif`,colorspace_fragment:`gl_FragColor = linearToOutputTexel( gl_FragColor );`,colorspace_pars_fragment:`vec4 LinearTransferOETF( in vec4 value ) {
	return value;
}
vec4 sRGBTransferEOTF( in vec4 value ) {
	return vec4( mix( pow( value.rgb * 0.9478672986 + vec3( 0.0521327014 ), vec3( 2.4 ) ), value.rgb * 0.0773993808, vec3( lessThanEqual( value.rgb, vec3( 0.04045 ) ) ) ), value.a );
}
vec4 sRGBTransferOETF( in vec4 value ) {
	return vec4( mix( pow( value.rgb, vec3( 0.41666 ) ) * 1.055 - vec3( 0.055 ), value.rgb * 12.92, vec3( lessThanEqual( value.rgb, vec3( 0.0031308 ) ) ) ), value.a );
}`,envmap_fragment:`#ifdef USE_ENVMAP
	#ifdef ENV_WORLDPOS
		vec3 cameraToFrag;
		if ( isOrthographic ) {
			cameraToFrag = normalize( vec3( - viewMatrix[ 0 ][ 2 ], - viewMatrix[ 1 ][ 2 ], - viewMatrix[ 2 ][ 2 ] ) );
		} else {
			cameraToFrag = normalize( vWorldPosition - cameraPosition );
		}
		vec3 worldNormal = transformNormalByInverseViewMatrix( normal, viewMatrix );
		#ifdef ENVMAP_MODE_REFLECTION
			vec3 reflectVec = reflect( cameraToFrag, worldNormal );
		#else
			vec3 reflectVec = refract( cameraToFrag, worldNormal, refractionRatio );
		#endif
	#else
		vec3 reflectVec = vReflect;
	#endif
	#ifdef ENVMAP_TYPE_CUBE
		vec4 envColor = textureCube( envMap, envMapRotation * reflectVec );
		#ifdef ENVMAP_BLENDING_MULTIPLY
			outgoingLight = mix( outgoingLight, outgoingLight * envColor.xyz, specularStrength * reflectivity );
		#elif defined( ENVMAP_BLENDING_MIX )
			outgoingLight = mix( outgoingLight, envColor.xyz, specularStrength * reflectivity );
		#elif defined( ENVMAP_BLENDING_ADD )
			outgoingLight += envColor.xyz * specularStrength * reflectivity;
		#endif
	#endif
#endif`,envmap_common_pars_fragment:`#ifdef USE_ENVMAP
	uniform float envMapIntensity;
	uniform mat3 envMapRotation;
	#ifdef ENVMAP_TYPE_CUBE
		uniform samplerCube envMap;
	#else
		uniform sampler2D envMap;
	#endif
#endif`,envmap_pars_fragment:`#ifdef USE_ENVMAP
	uniform float reflectivity;
	#if defined( USE_BUMPMAP ) || defined( USE_NORMALMAP ) || defined( PHONG ) || defined( LAMBERT )
		#define ENV_WORLDPOS
	#endif
	#ifdef ENV_WORLDPOS
		varying vec3 vWorldPosition;
		uniform float refractionRatio;
	#else
		varying vec3 vReflect;
	#endif
#endif`,envmap_pars_vertex:`#ifdef USE_ENVMAP
	#if defined( USE_BUMPMAP ) || defined( USE_NORMALMAP ) || defined( PHONG ) || defined( LAMBERT )
		#define ENV_WORLDPOS
	#endif
	#ifdef ENV_WORLDPOS
		
		varying vec3 vWorldPosition;
	#else
		varying vec3 vReflect;
		uniform float refractionRatio;
	#endif
#endif`,envmap_physical_pars_fragment:`#ifdef USE_ENVMAP
	vec3 getIBLIrradiance( const in vec3 normal ) {
		#ifdef ENVMAP_TYPE_CUBE_UV
			vec3 worldNormal = transformNormalByInverseViewMatrix( normal, viewMatrix );
			vec4 envMapColor = textureCubeUV( envMap, envMapRotation * worldNormal, 1.0 );
			return PI * envMapColor.rgb * envMapIntensity;
		#else
			return vec3( 0.0 );
		#endif
	}
	vec3 getIBLRadiance( const in vec3 viewDir, const in vec3 normal, const in float roughness ) {
		#ifdef ENVMAP_TYPE_CUBE_UV
			vec3 reflectVec = reflect( - viewDir, normal );
			reflectVec = normalize( mix( reflectVec, normal, pow4( roughness ) ) );
			reflectVec = transformDirectionByInverseViewMatrix( reflectVec, viewMatrix );
			vec4 envMapColor = textureCubeUV( envMap, envMapRotation * reflectVec, roughness );
			return envMapColor.rgb * envMapIntensity;
		#else
			return vec3( 0.0 );
		#endif
	}
	#ifdef USE_RETROREFLECTION
		vec3 getIBLRetroRadiance( const in vec3 viewDir, const in vec3 normal, const in float roughness ) {
			#ifdef ENVMAP_TYPE_CUBE_UV
				vec3 retroVec = normalize( mix( viewDir, normal, pow4( roughness ) ) );
				retroVec = transformDirectionByInverseViewMatrix( retroVec, viewMatrix );
				vec4 envMapColor = textureCubeUV( envMap, envMapRotation * retroVec, roughness );
				return envMapColor.rgb * envMapIntensity;
			#else
				return vec3( 0.0 );
			#endif
		}
	#endif
	#ifdef USE_ANISOTROPY
		vec3 getIBLAnisotropyRadiance( const in vec3 viewDir, const in vec3 normal, const in float roughness, const in vec3 bitangent, const in float anisotropy ) {
			#ifdef ENVMAP_TYPE_CUBE_UV
				vec3 bentNormal = cross( bitangent, viewDir );
				bentNormal = normalize( cross( bentNormal, bitangent ) );
				bentNormal = normalize( mix( bentNormal, normal, pow2( pow2( 1.0 - anisotropy * ( 1.0 - roughness ) ) ) ) );
				return getIBLRadiance( viewDir, bentNormal, roughness );
			#else
				return vec3( 0.0 );
			#endif
		}
		#ifdef USE_RETROREFLECTION
			vec3 getIBLAnisotropyRetroRadiance( const in vec3 viewDir, const in vec3 normal, const in float roughness, const in vec3 bitangent, const in float anisotropy ) {
				#ifdef ENVMAP_TYPE_CUBE_UV
					vec3 bentNormal = cross( bitangent, viewDir );
					bentNormal = normalize( cross( bentNormal, bitangent ) );
					bentNormal = normalize( mix( bentNormal, normal, pow2( pow2( 1.0 - anisotropy * ( 1.0 - roughness ) ) ) ) );
					return getIBLRetroRadiance( viewDir, bentNormal, roughness );
				#else
					return vec3( 0.0 );
				#endif
			}
		#endif
	#endif
#endif`,envmap_vertex:`#ifdef USE_ENVMAP
	#ifdef ENV_WORLDPOS
		vWorldPosition = worldPosition.xyz;
	#else
		vec3 cameraToVertex;
		if ( isOrthographic ) {
			cameraToVertex = normalize( vec3( - viewMatrix[ 0 ][ 2 ], - viewMatrix[ 1 ][ 2 ], - viewMatrix[ 2 ][ 2 ] ) );
		} else {
			cameraToVertex = normalize( worldPosition.xyz - cameraPosition );
		}
		vec3 worldNormal = transformNormalByInverseViewMatrix( transformedNormal, viewMatrix );
		#ifdef ENVMAP_MODE_REFLECTION
			vReflect = reflect( cameraToVertex, worldNormal );
		#else
			vReflect = refract( cameraToVertex, worldNormal, refractionRatio );
		#endif
	#endif
#endif`,fog_vertex:`#ifdef USE_FOG
	vFogDepth = - mvPosition.z;
#endif`,fog_pars_vertex:`#ifdef USE_FOG
	varying float vFogDepth;
#endif`,fog_fragment:`#ifdef USE_FOG
	#ifdef FOG_EXP2
		float fogFactor = 1.0 - exp( - fogDensity * fogDensity * vFogDepth * vFogDepth );
	#else
		float fogFactor = smoothstep( fogNear, fogFar, vFogDepth );
	#endif
	gl_FragColor.rgb = mix( gl_FragColor.rgb, fogColor, fogFactor );
#endif`,fog_pars_fragment:`#ifdef USE_FOG
	uniform vec3 fogColor;
	varying float vFogDepth;
	#ifdef FOG_EXP2
		uniform float fogDensity;
	#else
		uniform float fogNear;
		uniform float fogFar;
	#endif
#endif`,gradientmap_pars_fragment:`#ifdef USE_GRADIENTMAP
	uniform sampler2D gradientMap;
#endif
vec3 getGradientIrradiance( vec3 normal, vec3 lightDirection ) {
	float dotNL = dot( normal, lightDirection );
	vec2 coord = vec2( dotNL * 0.5 + 0.5, 0.0 );
	#ifdef USE_GRADIENTMAP
		return vec3( texture2D( gradientMap, coord ).r );
	#else
		vec2 fw = fwidth( coord ) * 0.5;
		return mix( vec3( 0.7 ), vec3( 1.0 ), smoothstep( 0.7 - fw.x, 0.7 + fw.x, coord.x ) );
	#endif
}`,lightmap_pars_fragment:`#ifdef USE_LIGHTMAP
	uniform sampler2D lightMap;
	uniform float lightMapIntensity;
#endif`,lights_lambert_fragment:`LambertMaterial material;
material.diffuseColor = diffuseColor.rgb;
material.specularStrength = specularStrength;`,lights_lambert_pars_fragment:`varying vec3 vViewPosition;
struct LambertMaterial {
	vec3 diffuseColor;
	float specularStrength;
};
void RE_Direct_Lambert( const in IncidentLight directLight, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in LambertMaterial material, inout ReflectedLight reflectedLight ) {
	float dotNL = saturate( dot( geometryNormal, directLight.direction ) );
	vec3 irradiance = dotNL * directLight.color;
	reflectedLight.directDiffuse += irradiance * BRDF_Lambert( material.diffuseColor );
}
void RE_IndirectDiffuse_Lambert( const in vec3 irradiance, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in LambertMaterial material, inout ReflectedLight reflectedLight ) {
	reflectedLight.indirectDiffuse += irradiance * BRDF_Lambert( material.diffuseColor );
}
#define RE_Direct				RE_Direct_Lambert
#define RE_IndirectDiffuse		RE_IndirectDiffuse_Lambert`,lights_pars_begin:`uniform bool receiveShadow;
uniform vec3 ambientLightColor;
#if defined( USE_LIGHT_PROBES )
	uniform vec3 lightProbe[ 9 ];
#endif
vec3 shGetIrradianceAt( in vec3 normal, in vec3 shCoefficients[ 9 ] ) {
	float x = normal.x, y = normal.y, z = normal.z;
	vec3 result = shCoefficients[ 0 ] * 0.886227;
	result += shCoefficients[ 1 ] * 2.0 * 0.511664 * y;
	result += shCoefficients[ 2 ] * 2.0 * 0.511664 * z;
	result += shCoefficients[ 3 ] * 2.0 * 0.511664 * x;
	result += shCoefficients[ 4 ] * 2.0 * 0.429043 * x * y;
	result += shCoefficients[ 5 ] * 2.0 * 0.429043 * y * z;
	result += shCoefficients[ 6 ] * ( 0.743125 * z * z - 0.247708 );
	result += shCoefficients[ 7 ] * 2.0 * 0.429043 * x * z;
	result += shCoefficients[ 8 ] * 0.429043 * ( x * x - y * y );
	return result;
}
vec3 getLightProbeIrradiance( const in vec3 lightProbe[ 9 ], const in vec3 normal ) {
	vec3 worldNormal = transformNormalByInverseViewMatrix( normal, viewMatrix );
	vec3 irradiance = shGetIrradianceAt( worldNormal, lightProbe );
	return irradiance;
}
vec3 getAmbientLightIrradiance( const in vec3 ambientLightColor ) {
	vec3 irradiance = ambientLightColor;
	return irradiance;
}
float getDistanceAttenuation( const in float lightDistance, const in float cutoffDistance, const in float decayExponent ) {
	float distanceFalloff = 1.0 / max( pow( lightDistance, decayExponent ), 0.01 );
	if ( cutoffDistance > 0.0 ) {
		distanceFalloff *= pow2( saturate( 1.0 - pow4( lightDistance / cutoffDistance ) ) );
	}
	return distanceFalloff;
}
float getSpotAttenuation( const in float coneCosine, const in float penumbraCosine, const in float angleCosine ) {
	return smoothstep( coneCosine, penumbraCosine, angleCosine );
}
#if NUM_SUN_LIGHTS > 0
	struct SunLight {
		vec3 direction;
		vec3 color;
	};
	uniform SunLight sunLights[ NUM_SUN_LIGHTS ];
	void getSunLightInfo( const in SunLight sunLight, out IncidentLight light ) {
		light.color = sunLight.color;
		light.direction = sunLight.direction;
		light.visible = true;
	}
#endif
#if NUM_DIR_LIGHTS > 0
	struct DirectionalLight {
		vec3 direction;
		vec3 color;
	};
	uniform DirectionalLight directionalLights[ NUM_DIR_LIGHTS ];
	void getDirectionalLightInfo( const in DirectionalLight directionalLight, out IncidentLight light ) {
		light.color = directionalLight.color;
		light.direction = directionalLight.direction;
		light.visible = true;
	}
#endif
#if NUM_POINT_LIGHTS > 0
	struct PointLight {
		vec3 position;
		vec3 color;
		float distance;
		float decay;
	};
	uniform PointLight pointLights[ NUM_POINT_LIGHTS ];
	void getPointLightInfo( const in PointLight pointLight, const in vec3 geometryPosition, out IncidentLight light ) {
		vec3 lVector = pointLight.position - geometryPosition;
		light.direction = normalize( lVector );
		float lightDistance = length( lVector );
		light.color = pointLight.color;
		light.color *= getDistanceAttenuation( lightDistance, pointLight.distance, pointLight.decay );
		light.visible = ( light.color != vec3( 0.0 ) );
	}
#endif
#if NUM_SPOT_LIGHTS > 0
	struct SpotLight {
		vec3 position;
		vec3 direction;
		vec3 color;
		float distance;
		float decay;
		float coneCos;
		float penumbraCos;
	};
	uniform SpotLight spotLights[ NUM_SPOT_LIGHTS ];
	void getSpotLightInfo( const in SpotLight spotLight, const in vec3 geometryPosition, out IncidentLight light ) {
		vec3 lVector = spotLight.position - geometryPosition;
		light.direction = normalize( lVector );
		float angleCos = dot( light.direction, spotLight.direction );
		float spotAttenuation = getSpotAttenuation( spotLight.coneCos, spotLight.penumbraCos, angleCos );
		if ( spotAttenuation > 0.0 ) {
			float lightDistance = length( lVector );
			light.color = spotLight.color * spotAttenuation;
			light.color *= getDistanceAttenuation( lightDistance, spotLight.distance, spotLight.decay );
			light.visible = ( light.color != vec3( 0.0 ) );
		} else {
			light.color = vec3( 0.0 );
			light.visible = false;
		}
	}
#endif
#if NUM_RECT_AREA_LIGHTS > 0
	struct RectAreaLight {
		vec3 color;
		vec3 position;
		vec3 halfWidth;
		vec3 halfHeight;
	};
	uniform sampler2D ltc_1;	uniform sampler2D ltc_2;
	uniform RectAreaLight rectAreaLights[ NUM_RECT_AREA_LIGHTS ];
#endif
#if NUM_HEMI_LIGHTS > 0
	struct HemisphereLight {
		vec3 direction;
		vec3 skyColor;
		vec3 groundColor;
	};
	uniform HemisphereLight hemisphereLights[ NUM_HEMI_LIGHTS ];
	vec3 getHemisphereLightIrradiance( const in HemisphereLight hemiLight, const in vec3 normal ) {
		float dotNL = dot( normal, hemiLight.direction );
		float hemiDiffuseWeight = 0.5 * dotNL + 0.5;
		vec3 irradiance = mix( hemiLight.groundColor, hemiLight.skyColor, hemiDiffuseWeight );
		return irradiance;
	}
#endif
#include <lightprobes_pars_fragment>`,lights_toon_fragment:`ToonMaterial material;
material.diffuseColor = diffuseColor.rgb;`,lights_toon_pars_fragment:`varying vec3 vViewPosition;
struct ToonMaterial {
	vec3 diffuseColor;
};
void RE_Direct_Toon( const in IncidentLight directLight, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in ToonMaterial material, inout ReflectedLight reflectedLight ) {
	vec3 irradiance = getGradientIrradiance( geometryNormal, directLight.direction ) * directLight.color;
	reflectedLight.directDiffuse += irradiance * BRDF_Lambert( material.diffuseColor );
}
void RE_IndirectDiffuse_Toon( const in vec3 irradiance, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in ToonMaterial material, inout ReflectedLight reflectedLight ) {
	reflectedLight.indirectDiffuse += irradiance * BRDF_Lambert( material.diffuseColor );
}
#define RE_Direct				RE_Direct_Toon
#define RE_IndirectDiffuse		RE_IndirectDiffuse_Toon`,lights_phong_fragment:`BlinnPhongMaterial material;
material.diffuseColor = diffuseColor.rgb;
material.specularColor = specular;
material.specularShininess = shininess;
material.specularStrength = specularStrength;`,lights_phong_pars_fragment:`varying vec3 vViewPosition;
struct BlinnPhongMaterial {
	vec3 diffuseColor;
	vec3 specularColor;
	float specularShininess;
	float specularStrength;
};
void RE_Direct_BlinnPhong( const in IncidentLight directLight, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in BlinnPhongMaterial material, inout ReflectedLight reflectedLight ) {
	float dotNL = saturate( dot( geometryNormal, directLight.direction ) );
	vec3 irradiance = dotNL * directLight.color;
	reflectedLight.directDiffuse += irradiance * BRDF_Lambert( material.diffuseColor );
	reflectedLight.directSpecular += irradiance * BRDF_BlinnPhong( directLight.direction, geometryViewDir, geometryNormal, material.specularColor, material.specularShininess ) * material.specularStrength;
}
void RE_IndirectDiffuse_BlinnPhong( const in vec3 irradiance, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in BlinnPhongMaterial material, inout ReflectedLight reflectedLight ) {
	reflectedLight.indirectDiffuse += irradiance * BRDF_Lambert( material.diffuseColor );
}
#define RE_Direct				RE_Direct_BlinnPhong
#define RE_IndirectDiffuse		RE_IndirectDiffuse_BlinnPhong`,lights_physical_fragment:`PhysicalMaterial material;
material.diffuseColor = diffuseColor.rgb;
material.diffuseContribution = diffuseColor.rgb * ( 1.0 - metalnessFactor );
material.metalness = metalnessFactor;
vec3 dxy = max( abs( dFdx( nonPerturbedNormal ) ), abs( dFdy( nonPerturbedNormal ) ) );
float geometryRoughness = max( max( dxy.x, dxy.y ), dxy.z );
material.roughness = max( roughnessFactor, 0.0525 );material.roughness += geometryRoughness;
material.roughness = min( material.roughness, 1.0 );
#ifdef IOR
	material.ior = ior;
	#ifdef USE_SPECULAR
		float specularIntensityFactor = specularIntensity;
		vec3 specularColorFactor = specularColor;
		#ifdef USE_SPECULAR_COLORMAP
			specularColorFactor *= texture2D( specularColorMap, vSpecularColorMapUv ).rgb;
		#endif
		#ifdef USE_SPECULAR_INTENSITYMAP
			specularIntensityFactor *= texture2D( specularIntensityMap, vSpecularIntensityMapUv ).a;
		#endif
		material.specularF90 = mix( specularIntensityFactor, 1.0, metalnessFactor );
	#else
		float specularIntensityFactor = 1.0;
		vec3 specularColorFactor = vec3( 1.0 );
		material.specularF90 = 1.0;
	#endif
	material.specularColor = min( pow2( ( material.ior - 1.0 ) / ( material.ior + 1.0 ) ) * specularColorFactor, vec3( 1.0 ) ) * specularIntensityFactor;
	material.specularColorBlended = mix( material.specularColor, diffuseColor.rgb, metalnessFactor );
#else
	material.specularColor = vec3( 0.04 );
	material.specularColorBlended = mix( material.specularColor, diffuseColor.rgb, metalnessFactor );
	material.specularF90 = 1.0;
#endif
#ifdef USE_CLEARCOAT
	material.clearcoat = clearcoat;
	material.clearcoatRoughness = clearcoatRoughness;
	material.clearcoatF0 = vec3( 0.04 );
	material.clearcoatF90 = 1.0;
	#ifdef USE_CLEARCOATMAP
		material.clearcoat *= texture2D( clearcoatMap, vClearcoatMapUv ).x;
	#endif
	#ifdef USE_CLEARCOAT_ROUGHNESSMAP
		material.clearcoatRoughness *= texture2D( clearcoatRoughnessMap, vClearcoatRoughnessMapUv ).y;
	#endif
	material.clearcoat = saturate( material.clearcoat );	material.clearcoatRoughness = max( material.clearcoatRoughness, 0.0525 );
	material.clearcoatRoughness += geometryRoughness;
	material.clearcoatRoughness = min( material.clearcoatRoughness, 1.0 );
#endif
#ifdef USE_DISPERSION
	material.dispersion = dispersion;
#endif
#ifdef USE_RETROREFLECTION
	material.retroreflectivity = retroreflectivity;
#endif
#ifdef USE_IRIDESCENCE
	material.iridescence = iridescence;
	material.iridescenceIOR = iridescenceIOR;
	#ifdef USE_IRIDESCENCEMAP
		material.iridescence *= texture2D( iridescenceMap, vIridescenceMapUv ).r;
	#endif
	#ifdef USE_IRIDESCENCE_THICKNESSMAP
		material.iridescenceThickness = (iridescenceThicknessMaximum - iridescenceThicknessMinimum) * texture2D( iridescenceThicknessMap, vIridescenceThicknessMapUv ).g + iridescenceThicknessMinimum;
	#else
		material.iridescenceThickness = iridescenceThicknessMaximum;
	#endif
#endif
#ifdef USE_SHEEN
	material.sheenColor = sheenColor;
	#ifdef USE_SHEEN_COLORMAP
		material.sheenColor *= texture2D( sheenColorMap, vSheenColorMapUv ).rgb;
	#endif
	material.sheenRoughness = clamp( sheenRoughness, 0.0001, 1.0 );
	#ifdef USE_SHEEN_ROUGHNESSMAP
		material.sheenRoughness *= texture2D( sheenRoughnessMap, vSheenRoughnessMapUv ).a;
	#endif
#endif
#ifdef USE_ANISOTROPY
	#ifdef USE_ANISOTROPYMAP
		mat2 anisotropyMat = mat2( anisotropyVector.x, anisotropyVector.y, - anisotropyVector.y, anisotropyVector.x );
		vec3 anisotropyPolar = texture2D( anisotropyMap, vAnisotropyMapUv ).rgb;
		vec2 anisotropyV = anisotropyMat * normalize( 2.0 * anisotropyPolar.rg - vec2( 1.0 ) ) * anisotropyPolar.b;
	#else
		vec2 anisotropyV = anisotropyVector;
	#endif
	material.anisotropy = length( anisotropyV );
	if( material.anisotropy == 0.0 ) {
		anisotropyV = vec2( 1.0, 0.0 );
	} else {
		anisotropyV /= material.anisotropy;
		material.anisotropy = saturate( material.anisotropy );
	}
	material.alphaT = mix( pow2( material.roughness ), 1.0, pow2( material.anisotropy ) );
	material.anisotropyT = tbn[ 0 ] * anisotropyV.x + tbn[ 1 ] * anisotropyV.y;
	material.anisotropyB = tbn[ 1 ] * anisotropyV.x - tbn[ 0 ] * anisotropyV.y;
#endif`,lights_physical_pars_fragment:`uniform sampler2D dfgLUT;
struct PhysicalMaterial {
	vec3 diffuseColor;
	vec3 diffuseContribution;
	vec3 specularColor;
	vec3 specularColorBlended;
	float roughness;
	float metalness;
	float specularF90;
	float dispersion;
	vec2 dfg;
	vec3 multiScatteringCompensation;
	#ifdef USE_RETROREFLECTION
		float retroreflectivity;
	#endif
	#ifdef USE_CLEARCOAT
		float clearcoat;
		float clearcoatRoughness;
		vec3 clearcoatF0;
		float clearcoatF90;
	#endif
	#ifdef USE_IRIDESCENCE
		float iridescence;
		float iridescenceIOR;
		float iridescenceThickness;
		vec3 iridescenceFresnel;
		vec3 iridescenceF0Dielectric;
		vec3 iridescenceF0Metallic;
	#endif
	#ifdef USE_SHEEN
		vec3 sheenColor;
		float sheenRoughness;
	#endif
	#ifdef IOR
		float ior;
	#endif
	#ifdef USE_TRANSMISSION
		float transmission;
		float transmissionAlpha;
		float thickness;
		float attenuationDistance;
		vec3 attenuationColor;
	#endif
	#ifdef USE_ANISOTROPY
		float anisotropy;
		float alphaT;
		vec3 anisotropyT;
		vec3 anisotropyB;
	#endif
};
vec3 clearcoatSpecularDirect = vec3( 0.0 );
vec3 clearcoatSpecularIndirect = vec3( 0.0 );
vec3 sheenSpecularDirect = vec3( 0.0 );
vec3 sheenSpecularIndirect = vec3(0.0 );
vec3 Schlick_to_F0( const in vec3 f, const in float f90, const in float dotVH ) {
    float x = clamp( 1.0 - dotVH, 0.0, 1.0 );
    float x2 = x * x;
    float x5 = clamp( x * x2 * x2, 0.0, 0.9999 );
    return ( f - vec3( f90 ) * x5 ) / ( 1.0 - x5 );
}
float V_GGX_SmithCorrelated( const in float alpha, const in float dotNL, const in float dotNV ) {
	float a2 = pow2( alpha );
	float gv = dotNL * sqrt( a2 + ( 1.0 - a2 ) * pow2( dotNV ) );
	float gl = dotNV * sqrt( a2 + ( 1.0 - a2 ) * pow2( dotNL ) );
	return 0.5 / max( gv + gl, EPSILON );
}
float D_GGX( const in float alpha, const in float dotNH ) {
	float a2 = pow2( alpha );
	float denom = pow2( dotNH ) * ( a2 - 1.0 ) + 1.0;
	return RECIPROCAL_PI * a2 / pow2( denom );
}
#ifdef USE_ANISOTROPY
	float V_GGX_SmithCorrelated_Anisotropic( const in float alphaT, const in float alphaB, const in float dotTV, const in float dotBV, const in float dotTL, const in float dotBL, const in float dotNV, const in float dotNL ) {
		float gv = dotNL * length( vec3( alphaT * dotTV, alphaB * dotBV, dotNV ) );
		float gl = dotNV * length( vec3( alphaT * dotTL, alphaB * dotBL, dotNL ) );
		return 0.5 / max( gv + gl, EPSILON );
	}
	float D_GGX_Anisotropic( const in float alphaT, const in float alphaB, const in float dotNH, const in float dotTH, const in float dotBH ) {
		float a2 = alphaT * alphaB;
		highp vec3 v = vec3( alphaB * dotTH, alphaT * dotBH, a2 * dotNH );
		highp float v2 = dot( v, v );
		float w2 = a2 / v2;
		return RECIPROCAL_PI * a2 * pow2 ( w2 );
	}
#endif
#ifdef USE_CLEARCOAT
	vec3 BRDF_GGX_Clearcoat( const in vec3 lightDir, const in vec3 viewDir, const in vec3 normal, const in PhysicalMaterial material) {
		vec3 f0 = material.clearcoatF0;
		float f90 = material.clearcoatF90;
		float roughness = material.clearcoatRoughness;
		float alpha = pow2( roughness );
		vec3 halfDir = normalize( lightDir + viewDir );
		float dotNL = saturate( dot( normal, lightDir ) );
		float dotNV = saturate( dot( normal, viewDir ) );
		float dotNH = saturate( dot( normal, halfDir ) );
		float dotVH = saturate( dot( viewDir, halfDir ) );
		vec3 F = F_Schlick( f0, f90, dotVH );
		float V = V_GGX_SmithCorrelated( alpha, dotNL, dotNV );
		float D = D_GGX( alpha, dotNH );
		return F * ( V * D );
	}
#endif
vec3 BRDF_GGX( const in vec3 lightDir, const in vec3 viewDir, const in vec3 normal, const in PhysicalMaterial material ) {
	vec3 f0 = material.specularColorBlended;
	float f90 = material.specularF90;
	float roughness = material.roughness;
	float alpha = pow2( roughness );
	vec3 halfDir = normalize( lightDir + viewDir );
	float dotNL = saturate( dot( normal, lightDir ) );
	float dotNV = saturate( dot( normal, viewDir ) );
	float dotNH = saturate( dot( normal, halfDir ) );
	float dotVH = saturate( dot( viewDir, halfDir ) );
	vec3 F = F_Schlick( f0, f90, dotVH );
	#ifdef USE_IRIDESCENCE
		F = mix( F, material.iridescenceFresnel, material.iridescence );
	#endif
	#ifdef USE_ANISOTROPY
		float dotTL = dot( material.anisotropyT, lightDir );
		float dotTV = dot( material.anisotropyT, viewDir );
		float dotTH = dot( material.anisotropyT, halfDir );
		float dotBL = dot( material.anisotropyB, lightDir );
		float dotBV = dot( material.anisotropyB, viewDir );
		float dotBH = dot( material.anisotropyB, halfDir );
		float V = V_GGX_SmithCorrelated_Anisotropic( material.alphaT, alpha, dotTV, dotBV, dotTL, dotBL, dotNV, dotNL );
		float D = D_GGX_Anisotropic( material.alphaT, alpha, dotNH, dotTH, dotBH );
	#else
		float V = V_GGX_SmithCorrelated( alpha, dotNL, dotNV );
		float D = D_GGX( alpha, dotNH );
	#endif
	return F * ( V * D );
}
vec2 LTC_Uv( const in vec3 N, const in vec3 V, const in float roughness ) {
	const float LUT_SIZE = 64.0;
	const float LUT_SCALE = ( LUT_SIZE - 1.0 ) / LUT_SIZE;
	const float LUT_BIAS = 0.5 / LUT_SIZE;
	float dotNV = saturate( dot( N, V ) );
	vec2 uv = vec2( roughness, sqrt( 1.0 - dotNV ) );
	uv = uv * LUT_SCALE + LUT_BIAS;
	return uv;
}
float LTC_ClippedSphereFormFactor( const in vec3 f ) {
	float l = length( f );
	return max( ( l * l + f.z ) / ( l + 1.0 ), 0.0 );
}
vec3 LTC_EdgeVectorFormFactor( const in vec3 v1, const in vec3 v2 ) {
	float x = dot( v1, v2 );
	float y = abs( x );
	float a = 0.8543985 + ( 0.4965155 + 0.0145206 * y ) * y;
	float b = 3.4175940 + ( 4.1616724 + y ) * y;
	float v = a / b;
	float theta_sintheta = ( x > 0.0 ) ? v : 0.5 * inversesqrt( max( 1.0 - x * x, 1e-7 ) ) - v;
	return cross( v1, v2 ) * theta_sintheta;
}
vec3 LTC_Evaluate( const in vec3 N, const in vec3 V, const in vec3 P, const in mat3 mInv, const in vec3 rectCoords[ 4 ] ) {
	vec3 v1 = rectCoords[ 1 ] - rectCoords[ 0 ];
	vec3 v2 = rectCoords[ 3 ] - rectCoords[ 0 ];
	vec3 lightNormal = cross( v1, v2 );
	if( dot( lightNormal, P - rectCoords[ 0 ] ) < 0.0 ) return vec3( 0.0 );
	vec3 T1, T2;
	T1 = normalize( V - N * dot( V, N ) );
	T2 = - cross( N, T1 );
	mat3 mat = mInv * transpose( mat3( T1, T2, N ) );
	vec3 coords[ 4 ];
	coords[ 0 ] = mat * ( rectCoords[ 0 ] - P );
	coords[ 1 ] = mat * ( rectCoords[ 1 ] - P );
	coords[ 2 ] = mat * ( rectCoords[ 2 ] - P );
	coords[ 3 ] = mat * ( rectCoords[ 3 ] - P );
	coords[ 0 ] = normalize( coords[ 0 ] );
	coords[ 1 ] = normalize( coords[ 1 ] );
	coords[ 2 ] = normalize( coords[ 2 ] );
	coords[ 3 ] = normalize( coords[ 3 ] );
	vec3 vectorFormFactor = vec3( 0.0 );
	vectorFormFactor += LTC_EdgeVectorFormFactor( coords[ 0 ], coords[ 1 ] );
	vectorFormFactor += LTC_EdgeVectorFormFactor( coords[ 1 ], coords[ 2 ] );
	vectorFormFactor += LTC_EdgeVectorFormFactor( coords[ 2 ], coords[ 3 ] );
	vectorFormFactor += LTC_EdgeVectorFormFactor( coords[ 3 ], coords[ 0 ] );
	float result = LTC_ClippedSphereFormFactor( vectorFormFactor );
	return vec3( result );
}
#if defined( USE_SHEEN )
float D_Charlie( float roughness, float dotNH ) {
	float alpha = pow2( roughness );
	float invAlpha = 1.0 / alpha;
	float cos2h = dotNH * dotNH;
	float sin2h = max( 1.0 - cos2h, 0.0078125 );
	return ( 2.0 + invAlpha ) * pow( sin2h, invAlpha * 0.5 ) / ( 2.0 * PI );
}
float V_Neubelt( float dotNV, float dotNL ) {
	return saturate( 1.0 / ( 4.0 * ( dotNL + dotNV - dotNL * dotNV ) ) );
}
vec3 BRDF_Sheen( const in vec3 lightDir, const in vec3 viewDir, const in vec3 normal, vec3 sheenColor, const in float sheenRoughness ) {
	vec3 halfDir = normalize( lightDir + viewDir );
	float dotNL = saturate( dot( normal, lightDir ) );
	float dotNV = saturate( dot( normal, viewDir ) );
	float dotNH = saturate( dot( normal, halfDir ) );
	float D = D_Charlie( sheenRoughness, dotNH );
	float V = V_Neubelt( dotNV, dotNL );
	return sheenColor * ( D * V );
}
#endif
float IBLSheenBRDF( const in vec3 normal, const in vec3 viewDir, const in float roughness ) {
	float dotNV = saturate( dot( normal, viewDir ) );
	float r2 = roughness * roughness;
	float rInv = 1.0 / ( roughness + 0.1 );
	float a = -1.9362 + 1.0678 * roughness + 0.4573 * r2 - 0.8469 * rInv;
	float b = -0.6014 + 0.5538 * roughness - 0.4670 * r2 - 0.1255 * rInv;
	float DG = exp( a * dotNV + b );
	return saturate( DG );
}
vec3 EnvironmentBRDF( const in vec3 normal, const in vec3 viewDir, const in vec3 specularColor, const in float specularF90, const in float roughness ) {
	float dotNV = saturate( dot( normal, viewDir ) );
	vec2 fab = texture2D( dfgLUT, vec2( roughness, dotNV ) ).rg;
	return specularColor * fab.x + specularF90 * fab.y;
}
#ifdef USE_IRIDESCENCE
void computeMultiscatteringIridescence( const in vec2 fab, const in vec3 specularColor, const in float specularF90, const in float iridescence, const in vec3 iridescenceF0, inout vec3 singleScatter, inout vec3 multiScatter ) {
#else
void computeMultiscattering( const in vec2 fab, const in vec3 specularColor, const in float specularF90, inout vec3 singleScatter, inout vec3 multiScatter ) {
#endif
	#ifdef USE_IRIDESCENCE
		vec3 Fr = mix( specularColor, iridescenceF0, iridescence );
	#else
		vec3 Fr = specularColor;
	#endif
	vec3 FssEss = Fr * fab.x + specularF90 * fab.y;
	float Ess = fab.x + fab.y;
	float Ems = 1.0 - Ess;
	vec3 Favg = Fr + ( 1.0 - Fr ) * 0.047619;	vec3 Fms = FssEss * Favg / ( 1.0 - Ems * Favg );
	singleScatter += FssEss;
	multiScatter += Fms * Ems;
}
#if NUM_RECT_AREA_LIGHTS > 0
	void RE_Direct_RectArea_Physical( const in RectAreaLight rectAreaLight, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in PhysicalMaterial material, inout ReflectedLight reflectedLight ) {
		vec3 normal = geometryNormal;
		vec3 viewDir = geometryViewDir;
		vec3 position = geometryPosition;
		vec3 lightPos = rectAreaLight.position;
		vec3 halfWidth = rectAreaLight.halfWidth;
		vec3 halfHeight = rectAreaLight.halfHeight;
		vec3 lightColor = rectAreaLight.color;
		float roughness = material.roughness;
		vec3 rectCoords[ 4 ];
		rectCoords[ 0 ] = lightPos + halfWidth - halfHeight;		rectCoords[ 1 ] = lightPos - halfWidth - halfHeight;
		rectCoords[ 2 ] = lightPos - halfWidth + halfHeight;
		rectCoords[ 3 ] = lightPos + halfWidth + halfHeight;
		vec2 uv = LTC_Uv( normal, viewDir, roughness );
		vec4 t1 = texture2D( ltc_1, uv );
		vec4 t2 = texture2D( ltc_2, uv );
		mat3 mInv = mat3(
			vec3( t1.x, 0, t1.y ),
			vec3(    0, 1,    0 ),
			vec3( t1.z, 0, t1.w )
		);
		vec3 fresnel = ( material.specularColorBlended * t2.x + ( material.specularF90 - material.specularColorBlended ) * t2.y );
		reflectedLight.directSpecular += lightColor * fresnel * LTC_Evaluate( normal, viewDir, position, mInv, rectCoords );
		reflectedLight.directDiffuse += lightColor * material.diffuseContribution * LTC_Evaluate( normal, viewDir, position, mat3( 1.0 ), rectCoords );
		#ifdef USE_CLEARCOAT
			vec3 Ncc = geometryClearcoatNormal;
			vec2 uvClearcoat = LTC_Uv( Ncc, viewDir, material.clearcoatRoughness );
			vec4 t1Clearcoat = texture2D( ltc_1, uvClearcoat );
			vec4 t2Clearcoat = texture2D( ltc_2, uvClearcoat );
			mat3 mInvClearcoat = mat3(
				vec3( t1Clearcoat.x, 0, t1Clearcoat.y ),
				vec3(             0, 1,             0 ),
				vec3( t1Clearcoat.z, 0, t1Clearcoat.w )
			);
			vec3 fresnelClearcoat = material.clearcoatF0 * t2Clearcoat.x + ( material.clearcoatF90 - material.clearcoatF0 ) * t2Clearcoat.y;
			clearcoatSpecularDirect += lightColor * fresnelClearcoat * LTC_Evaluate( Ncc, viewDir, position, mInvClearcoat, rectCoords );
		#endif
	}
#endif
void RE_Direct_Physical( const in IncidentLight directLight, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in PhysicalMaterial material, inout ReflectedLight reflectedLight ) {
	float dotNL = saturate( dot( geometryNormal, directLight.direction ) );
	vec3 irradiance = dotNL * directLight.color;
	#ifdef USE_CLEARCOAT
		float dotNLcc = saturate( dot( geometryClearcoatNormal, directLight.direction ) );
		vec3 ccIrradiance = dotNLcc * directLight.color;
		clearcoatSpecularDirect += ccIrradiance * BRDF_GGX_Clearcoat( directLight.direction, geometryViewDir, geometryClearcoatNormal, material );
	#endif
	#ifdef USE_SHEEN
 
 		sheenSpecularDirect += irradiance * BRDF_Sheen( directLight.direction, geometryViewDir, geometryNormal, material.sheenColor, material.sheenRoughness );
 
 		float sheenAlbedoV = IBLSheenBRDF( geometryNormal, geometryViewDir, material.sheenRoughness );
 		float sheenAlbedoL = IBLSheenBRDF( geometryNormal, directLight.direction, material.sheenRoughness );
 
 		float sheenEnergyComp = 1.0 - max3( material.sheenColor ) * max( sheenAlbedoV, sheenAlbedoL );
 
 		irradiance *= sheenEnergyComp;
 
 	#endif
	vec3 specularBRDF = BRDF_GGX( directLight.direction, geometryViewDir, geometryNormal, material );
	#ifdef USE_RETROREFLECTION
		vec3 retroViewDir = reflect( - geometryViewDir, geometryNormal );
		vec3 retroSpecularBRDF = BRDF_GGX( directLight.direction, retroViewDir, geometryNormal, material );
		specularBRDF = mix( specularBRDF, retroSpecularBRDF, saturate( material.retroreflectivity ) );
	#endif
	reflectedLight.directSpecular += irradiance * specularBRDF * material.multiScatteringCompensation;
	vec3 halfDir = normalize( directLight.direction + geometryViewDir );
	float dotVH = saturate( dot( geometryViewDir, halfDir ) );
	vec3 F = F_Schlick( material.specularColor, material.specularF90, dotVH );
	#ifdef USE_RETROREFLECTION
		vec3 retroHalfDir = normalize( directLight.direction + retroViewDir );
		float dotRetroVH = saturate( dot( retroViewDir, retroHalfDir ) );
		vec3 retroF = F_Schlick( material.specularColor, material.specularF90, dotRetroVH );
		F = mix( F, retroF, saturate( material.retroreflectivity ) );
	#endif
	reflectedLight.directDiffuse += irradiance * BRDF_Lambert( material.diffuseContribution ) * ( 1.0 - F );
}
void RE_IndirectDiffuse_Physical( const in vec3 irradiance, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in PhysicalMaterial material, inout ReflectedLight reflectedLight ) {
	vec3 singleScattering = vec3( 0.0 );
	vec3 multiScattering = vec3( 0.0 );
	#ifdef USE_IRIDESCENCE
		computeMultiscatteringIridescence( material.dfg, material.specularColor, material.specularF90, material.iridescence, material.iridescenceF0Dielectric, singleScattering, multiScattering );
	#else
		computeMultiscattering( material.dfg, material.specularColor, material.specularF90, singleScattering, multiScattering );
	#endif
	vec3 diffuse = irradiance * BRDF_Lambert( material.diffuseContribution ) * ( 1.0 - singleScattering - multiScattering );
	#ifdef USE_SHEEN
		float sheenAlbedo = IBLSheenBRDF( geometryNormal, geometryViewDir, material.sheenRoughness );
		sheenSpecularIndirect += irradiance * material.sheenColor * sheenAlbedo * RECIPROCAL_PI;
		float sheenEnergyComp = 1.0 - max3( material.sheenColor ) * sheenAlbedo;
		diffuse *= sheenEnergyComp;
	#endif
	reflectedLight.indirectDiffuse += diffuse;
}
void RE_IndirectSpecular_Physical( const in vec3 radiance, const in vec3 irradiance, const in vec3 clearcoatRadiance, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in PhysicalMaterial material, inout ReflectedLight reflectedLight) {
	#ifdef USE_CLEARCOAT
		clearcoatSpecularIndirect += clearcoatRadiance * EnvironmentBRDF( geometryClearcoatNormal, geometryViewDir, material.clearcoatF0, material.clearcoatF90, material.clearcoatRoughness );
	#endif
	#ifdef USE_SHEEN
		sheenSpecularIndirect += irradiance * material.sheenColor * IBLSheenBRDF( geometryNormal, geometryViewDir, material.sheenRoughness ) * RECIPROCAL_PI;
 	#endif
	vec3 singleScatteringDielectric = vec3( 0.0 );
	vec3 multiScatteringDielectric = vec3( 0.0 );
	vec3 singleScatteringMetallic = vec3( 0.0 );
	vec3 multiScatteringMetallic = vec3( 0.0 );
	#ifdef USE_IRIDESCENCE
		computeMultiscatteringIridescence( material.dfg, material.specularColor, material.specularF90, material.iridescence, material.iridescenceF0Dielectric, singleScatteringDielectric, multiScatteringDielectric );
		computeMultiscatteringIridescence( material.dfg, material.diffuseColor, material.specularF90, material.iridescence, material.iridescenceF0Metallic, singleScatteringMetallic, multiScatteringMetallic );
	#else
		computeMultiscattering( material.dfg, material.specularColor, material.specularF90, singleScatteringDielectric, multiScatteringDielectric );
		computeMultiscattering( material.dfg, material.diffuseColor, material.specularF90, singleScatteringMetallic, multiScatteringMetallic );
	#endif
	vec3 singleScattering = mix( singleScatteringDielectric, singleScatteringMetallic, material.metalness );
	vec3 multiScattering = mix( multiScatteringDielectric, multiScatteringMetallic, material.metalness );
	vec3 totalScatteringDielectric = singleScatteringDielectric + multiScatteringDielectric;
	vec3 diffuse = material.diffuseContribution * ( 1.0 - totalScatteringDielectric );
	vec3 cosineWeightedIrradiance = irradiance * RECIPROCAL_PI;
	vec3 indirectSpecular = radiance * singleScattering;
	indirectSpecular += multiScattering * cosineWeightedIrradiance;
	vec3 indirectDiffuse = diffuse * cosineWeightedIrradiance;
	#ifdef USE_SHEEN
		float sheenAlbedo = IBLSheenBRDF( geometryNormal, geometryViewDir, material.sheenRoughness );
		float sheenEnergyComp = 1.0 - max3( material.sheenColor ) * sheenAlbedo;
		indirectSpecular *= sheenEnergyComp;
		indirectDiffuse *= sheenEnergyComp;
	#endif
	reflectedLight.indirectSpecular += indirectSpecular;
	reflectedLight.indirectDiffuse += indirectDiffuse;
}
#define RE_Direct				RE_Direct_Physical
#define RE_Direct_RectArea		RE_Direct_RectArea_Physical
#define RE_IndirectDiffuse		RE_IndirectDiffuse_Physical
#define RE_IndirectSpecular		RE_IndirectSpecular_Physical
float computeSpecularOcclusion( const in float dotNV, const in float ambientOcclusion, const in float roughness ) {
	return saturate( pow( dotNV + ambientOcclusion, exp2( - 16.0 * roughness - 1.0 ) ) - 1.0 + ambientOcclusion );
}`,lights_fragment_begin:`
vec3 geometryPosition = - vViewPosition;
vec3 geometryNormal = normal;
vec3 geometryViewDir = ( isOrthographic ) ? vec3( 0, 0, 1 ) : normalize( vViewPosition );
vec3 geometryClearcoatNormal = vec3( 0.0 );
#ifdef USE_CLEARCOAT
	geometryClearcoatNormal = clearcoatNormal;
#endif
#ifdef USE_IRIDESCENCE
	float dotNVi = saturate( dot( normal, geometryViewDir ) );
	if ( material.iridescenceThickness == 0.0 ) {
		material.iridescence = 0.0;
	} else {
		material.iridescence = saturate( material.iridescence );
	}
	if ( material.iridescence > 0.0 ) {
		vec3 iridescenceFresnelDielectric = evalIridescence( 1.0, material.iridescenceIOR, dotNVi, material.iridescenceThickness, material.specularColor );
		vec3 iridescenceFresnelMetallic = evalIridescence( 1.0, material.iridescenceIOR, dotNVi, material.iridescenceThickness, material.diffuseColor );
		material.iridescenceFresnel = mix( iridescenceFresnelDielectric, iridescenceFresnelMetallic, material.metalness );
		material.iridescenceF0Dielectric = Schlick_to_F0( iridescenceFresnelDielectric, 1.0, dotNVi );
		material.iridescenceF0Metallic = Schlick_to_F0( iridescenceFresnelMetallic, 1.0, dotNVi );
	}
#endif
#ifdef STANDARD
	float dotNVms = saturate( dot( geometryNormal, geometryViewDir ) );
	material.dfg = texture2D( dfgLUT, vec2( material.roughness, dotNVms ) ).rg;
	#if ( NUM_SUN_LIGHTS > 0 || NUM_DIR_LIGHTS > 0 || NUM_POINT_LIGHTS > 0 || NUM_SPOT_LIGHTS > 0 )
		float EssMs = material.dfg.x + material.dfg.y;
		material.multiScatteringCompensation = 1.0 + material.specularColorBlended * ( 1.0 / EssMs - 1.0 );
	#endif
#endif
IncidentLight directLight;
#if ( NUM_POINT_LIGHTS > 0 ) && defined( RE_Direct )
	PointLight pointLight;
	#if defined( USE_SHADOWMAP ) && NUM_POINT_LIGHT_SHADOWS > 0
	PointLightShadow pointLightShadow;
	#endif
	#pragma unroll_loop_start
	for ( int i = 0; i < NUM_POINT_LIGHTS; i ++ ) {
		pointLight = pointLights[ i ];
		getPointLightInfo( pointLight, geometryPosition, directLight );
		#if defined( USE_SHADOWMAP ) && ( UNROLLED_LOOP_INDEX < NUM_POINT_LIGHT_SHADOWS ) && ( defined( SHADOWMAP_TYPE_PCF ) || defined( SHADOWMAP_TYPE_BASIC ) )
		pointLightShadow = pointLightShadows[ i ];
		directLight.color *= ( directLight.visible && receiveShadow ) ? getPointShadow( pointShadowMap[ i ], pointLightShadow.shadowMapSize, pointLightShadow.shadowIntensity, pointLightShadow.shadowBias, pointLightShadow.shadowRadius, vPointShadowCoord[ i ], pointLightShadow.shadowCameraNear, pointLightShadow.shadowCameraFar ) : 1.0;
		#endif
		RE_Direct( directLight, geometryPosition, geometryNormal, geometryViewDir, geometryClearcoatNormal, material, reflectedLight );
	}
	#pragma unroll_loop_end
#endif
#if ( NUM_SPOT_LIGHTS > 0 ) && defined( RE_Direct )
	SpotLight spotLight;
	vec4 spotColor;
	vec3 spotLightCoord;
	bool inSpotLightMap;
	#if defined( USE_SHADOWMAP ) && NUM_SPOT_LIGHT_SHADOWS > 0
	SpotLightShadow spotLightShadow;
	#endif
	#pragma unroll_loop_start
	for ( int i = 0; i < NUM_SPOT_LIGHTS; i ++ ) {
		spotLight = spotLights[ i ];
		getSpotLightInfo( spotLight, geometryPosition, directLight );
		#if ( UNROLLED_LOOP_INDEX < NUM_SPOT_LIGHT_SHADOWS_WITH_MAPS )
		#define SPOT_LIGHT_MAP_INDEX UNROLLED_LOOP_INDEX
		#elif ( UNROLLED_LOOP_INDEX < NUM_SPOT_LIGHT_SHADOWS )
		#define SPOT_LIGHT_MAP_INDEX NUM_SPOT_LIGHT_MAPS
		#else
		#define SPOT_LIGHT_MAP_INDEX ( UNROLLED_LOOP_INDEX - NUM_SPOT_LIGHT_SHADOWS + NUM_SPOT_LIGHT_SHADOWS_WITH_MAPS )
		#endif
		#if ( SPOT_LIGHT_MAP_INDEX < NUM_SPOT_LIGHT_MAPS )
			spotLightCoord = vSpotLightCoord[ i ].xyz / vSpotLightCoord[ i ].w;
			inSpotLightMap = all( lessThan( abs( spotLightCoord * 2. - 1. ), vec3( 1.0 ) ) );
			spotColor = texture2D( spotLightMap[ SPOT_LIGHT_MAP_INDEX ], spotLightCoord.xy );
			directLight.color = inSpotLightMap ? directLight.color * spotColor.rgb : directLight.color;
		#endif
		#undef SPOT_LIGHT_MAP_INDEX
		#if defined( USE_SHADOWMAP ) && ( UNROLLED_LOOP_INDEX < NUM_SPOT_LIGHT_SHADOWS )
		spotLightShadow = spotLightShadows[ i ];
		directLight.color *= ( directLight.visible && receiveShadow ) ? getShadow( spotShadowMap[ i ], spotLightShadow.shadowMapSize, spotLightShadow.shadowIntensity, spotLightShadow.shadowBias, spotLightShadow.shadowRadius, vSpotLightCoord[ i ] ) : 1.0;
		#endif
		RE_Direct( directLight, geometryPosition, geometryNormal, geometryViewDir, geometryClearcoatNormal, material, reflectedLight );
	}
	#pragma unroll_loop_end
#endif
#if ( NUM_SUN_LIGHTS > 0 ) && defined( RE_Direct )
	SunLight sunLight;
	#if defined( USE_SHADOWMAP ) && NUM_SUN_LIGHT_SHADOWS > 0
	SunLightShadow sunLightShadow;
	#endif
	#pragma unroll_loop_start
	for ( int i = 0; i < NUM_SUN_LIGHTS; i ++ ) {
		sunLight = sunLights[ i ];
		getSunLightInfo( sunLight, directLight );
		#if defined( USE_SHADOWMAP ) && ( UNROLLED_LOOP_INDEX < NUM_SUN_LIGHT_SHADOWS )
		sunLightShadow = sunLightShadows[ i ];
		directLight.color *= ( directLight.visible && receiveShadow ) ? getSunShadow( sunShadowMap[ i ], sunLightShadow, UNROLLED_LOOP_INDEX ) : 1.0;
		#endif
		RE_Direct( directLight, geometryPosition, geometryNormal, geometryViewDir, geometryClearcoatNormal, material, reflectedLight );
	}
	#pragma unroll_loop_end
#endif
#if ( NUM_DIR_LIGHTS > 0 ) && defined( RE_Direct )
	DirectionalLight directionalLight;
	#if defined( USE_SHADOWMAP ) && NUM_DIR_LIGHT_SHADOWS > 0
	DirectionalLightShadow directionalLightShadow;
	#endif
	#pragma unroll_loop_start
	for ( int i = 0; i < NUM_DIR_LIGHTS; i ++ ) {
		directionalLight = directionalLights[ i ];
		getDirectionalLightInfo( directionalLight, directLight );
		#if defined( USE_SHADOWMAP ) && ( UNROLLED_LOOP_INDEX < NUM_DIR_LIGHT_SHADOWS )
		directionalLightShadow = directionalLightShadows[ i ];
		directLight.color *= ( directLight.visible && receiveShadow ) ? getShadow( directionalShadowMap[ i ], directionalLightShadow.shadowMapSize, directionalLightShadow.shadowIntensity, directionalLightShadow.shadowBias, directionalLightShadow.shadowRadius, vDirectionalShadowCoord[ i ] ) : 1.0;
		#endif
		RE_Direct( directLight, geometryPosition, geometryNormal, geometryViewDir, geometryClearcoatNormal, material, reflectedLight );
	}
	#pragma unroll_loop_end
#endif
#if ( NUM_RECT_AREA_LIGHTS > 0 ) && defined( RE_Direct_RectArea )
	RectAreaLight rectAreaLight;
	#pragma unroll_loop_start
	for ( int i = 0; i < NUM_RECT_AREA_LIGHTS; i ++ ) {
		rectAreaLight = rectAreaLights[ i ];
		RE_Direct_RectArea( rectAreaLight, geometryPosition, geometryNormal, geometryViewDir, geometryClearcoatNormal, material, reflectedLight );
	}
	#pragma unroll_loop_end
#endif
#if defined( RE_IndirectDiffuse )
	vec3 iblIrradiance = vec3( 0.0 );
	vec3 irradiance = getAmbientLightIrradiance( ambientLightColor );
	#if defined( USE_LIGHT_PROBES )
		irradiance += getLightProbeIrradiance( lightProbe, geometryNormal );
	#endif
	#if ( NUM_HEMI_LIGHTS > 0 )
		#pragma unroll_loop_start
		for ( int i = 0; i < NUM_HEMI_LIGHTS; i ++ ) {
			irradiance += getHemisphereLightIrradiance( hemisphereLights[ i ], geometryNormal );
		}
		#pragma unroll_loop_end
	#endif
	#ifdef USE_LIGHT_PROBES_GRID
		vec3 probeWorldPos = ( ( vec4( geometryPosition, 1.0 ) - viewMatrix[ 3 ] ) * viewMatrix ).xyz;
		vec3 probeWorldNormal = transformNormalByInverseViewMatrix( geometryNormal, viewMatrix );
		irradiance += getLightProbeGridIrradiance( probeWorldPos, probeWorldNormal );
	#endif
#endif
#if defined( RE_IndirectSpecular )
	vec3 radiance = vec3( 0.0 );
	vec3 clearcoatRadiance = vec3( 0.0 );
#endif`,lights_fragment_maps:`#if defined( RE_IndirectDiffuse )
	#ifdef USE_LIGHTMAP
		vec4 lightMapTexel = texture2D( lightMap, vLightMapUv );
		vec3 lightMapIrradiance = lightMapTexel.rgb * lightMapIntensity;
		irradiance += lightMapIrradiance;
	#endif
	#if defined( USE_ENVMAP ) && defined( ENVMAP_TYPE_CUBE_UV )
		#if defined( STANDARD ) || defined( LAMBERT ) || defined( PHONG )
			iblIrradiance += getIBLIrradiance( geometryNormal );
		#endif
	#endif
#endif
#if defined( USE_ENVMAP ) && defined( RE_IndirectSpecular )
	#ifdef USE_ANISOTROPY
		vec3 iblRadiance = getIBLAnisotropyRadiance( geometryViewDir, geometryNormal, material.roughness, material.anisotropyB, material.anisotropy );
	#else
		vec3 iblRadiance = getIBLRadiance( geometryViewDir, geometryNormal, material.roughness );
	#endif
	#ifdef USE_RETROREFLECTION
		#ifdef USE_ANISOTROPY
			vec3 retroIBLRadiance = getIBLAnisotropyRetroRadiance( geometryViewDir, geometryNormal, material.roughness, material.anisotropyB, material.anisotropy );
		#else
			vec3 retroIBLRadiance = getIBLRetroRadiance( geometryViewDir, geometryNormal, material.roughness );
		#endif
		iblRadiance = mix( iblRadiance, retroIBLRadiance, saturate( material.retroreflectivity ) );
	#endif
	radiance += iblRadiance;
	#ifdef USE_CLEARCOAT
		clearcoatRadiance += getIBLRadiance( geometryViewDir, geometryClearcoatNormal, material.clearcoatRoughness );
	#endif
#endif`,lights_fragment_end:`#if defined( RE_IndirectDiffuse )
	#if defined( LAMBERT ) || defined( PHONG )
		irradiance += iblIrradiance;
	#endif
	RE_IndirectDiffuse( irradiance, geometryPosition, geometryNormal, geometryViewDir, geometryClearcoatNormal, material, reflectedLight );
#endif
#if defined( RE_IndirectSpecular )
	RE_IndirectSpecular( radiance, iblIrradiance, clearcoatRadiance, geometryPosition, geometryNormal, geometryViewDir, geometryClearcoatNormal, material, reflectedLight );
#endif`,lightprobes_pars_fragment:`#ifdef USE_LIGHT_PROBES_GRID
uniform highp sampler3D probesSH;
uniform vec3 probesMin;
uniform vec3 probesMax;
uniform vec3 probesResolution;
vec3 getLightProbeGridIrradiance( vec3 worldPos, vec3 worldNormal ) {
	vec3 res = probesResolution;
	vec3 gridRange = probesMax - probesMin;
	vec3 resMinusOne = res - 1.0;
	vec3 probeSpacing = gridRange / resMinusOne;
	vec3 samplePos = worldPos + worldNormal * probeSpacing * 0.5;
	vec3 uvw = clamp( ( samplePos - probesMin ) / gridRange, 0.0, 1.0 );
	uvw = uvw * resMinusOne / res + 0.5 / res;
	float nz          = res.z;
	float paddedSlices = nz + 2.0;
	float atlasDepth  = 7.0 * paddedSlices;
	float uvZBase     = uvw.z * nz + 1.0;
	vec4 s0 = texture( probesSH, vec3( uvw.xy, ( uvZBase                       ) / atlasDepth ) );
	vec4 s1 = texture( probesSH, vec3( uvw.xy, ( uvZBase +       paddedSlices   ) / atlasDepth ) );
	vec4 s2 = texture( probesSH, vec3( uvw.xy, ( uvZBase + 2.0 * paddedSlices   ) / atlasDepth ) );
	vec4 s3 = texture( probesSH, vec3( uvw.xy, ( uvZBase + 3.0 * paddedSlices   ) / atlasDepth ) );
	vec4 s4 = texture( probesSH, vec3( uvw.xy, ( uvZBase + 4.0 * paddedSlices   ) / atlasDepth ) );
	vec4 s5 = texture( probesSH, vec3( uvw.xy, ( uvZBase + 5.0 * paddedSlices   ) / atlasDepth ) );
	vec4 s6 = texture( probesSH, vec3( uvw.xy, ( uvZBase + 6.0 * paddedSlices   ) / atlasDepth ) );
	vec3 c0 = s0.xyz;
	vec3 c1 = vec3( s0.w, s1.xy );
	vec3 c2 = vec3( s1.zw, s2.x );
	vec3 c3 = s2.yzw;
	vec3 c4 = s3.xyz;
	vec3 c5 = vec3( s3.w, s4.xy );
	vec3 c6 = vec3( s4.zw, s5.x );
	vec3 c7 = s5.yzw;
	vec3 c8 = s6.xyz;
	float x = worldNormal.x, y = worldNormal.y, z = worldNormal.z;
	vec3 result = c0 * 0.886227;
	result += c1 * 2.0 * 0.511664 * y;
	result += c2 * 2.0 * 0.511664 * z;
	result += c3 * 2.0 * 0.511664 * x;
	result += c4 * 2.0 * 0.429043 * x * y;
	result += c5 * 2.0 * 0.429043 * y * z;
	result += c6 * ( 0.743125 * z * z - 0.247708 );
	result += c7 * 2.0 * 0.429043 * x * z;
	result += c8 * 0.429043 * ( x * x - y * y );
	return max( result, vec3( 0.0 ) );
}
#endif`,logdepthbuf_fragment:`#if defined( USE_LOGARITHMIC_DEPTH_BUFFER )
	gl_FragDepth = vIsPerspective == 0.0 ? gl_FragCoord.z : log2( vFragDepth ) * logDepthBufFC * 0.5;
#endif`,logdepthbuf_pars_fragment:`#if defined( USE_LOGARITHMIC_DEPTH_BUFFER )
	uniform float logDepthBufFC;
	varying float vFragDepth;
	varying float vIsPerspective;
#endif`,logdepthbuf_pars_vertex:`#ifdef USE_LOGARITHMIC_DEPTH_BUFFER
	varying float vFragDepth;
	varying float vIsPerspective;
#endif`,logdepthbuf_vertex:`#ifdef USE_LOGARITHMIC_DEPTH_BUFFER
	vFragDepth = 1.0 + gl_Position.w;
	vIsPerspective = float( isPerspectiveMatrix( projectionMatrix ) );
#endif`,map_fragment:`#ifdef USE_MAP
	vec4 sampledDiffuseColor = texture2D( map, vMapUv );
	#ifdef DECODE_VIDEO_TEXTURE
		sampledDiffuseColor = sRGBTransferEOTF( sampledDiffuseColor );
	#endif
	diffuseColor *= sampledDiffuseColor;
#endif`,map_pars_fragment:`#ifdef USE_MAP
	uniform sampler2D map;
#endif`,map_particle_fragment:`#if defined( USE_MAP ) || defined( USE_ALPHAMAP )
	#if defined( USE_POINTS_UV )
		vec2 uv = vUv;
	#else
		vec2 uv = ( uvTransform * vec3( gl_PointCoord.x, 1.0 - gl_PointCoord.y, 1 ) ).xy;
	#endif
#endif
#ifdef USE_MAP
	diffuseColor *= texture2D( map, uv );
#endif
#ifdef USE_ALPHAMAP
	diffuseColor.a *= texture2D( alphaMap, uv ).g;
#endif`,map_particle_pars_fragment:`#if defined( USE_POINTS_UV )
	varying vec2 vUv;
#else
	#if defined( USE_MAP ) || defined( USE_ALPHAMAP )
		uniform mat3 uvTransform;
	#endif
#endif
#ifdef USE_MAP
	uniform sampler2D map;
#endif
#ifdef USE_ALPHAMAP
	uniform sampler2D alphaMap;
#endif`,metalnessmap_fragment:`float metalnessFactor = metalness;
#ifdef USE_METALNESSMAP
	vec4 texelMetalness = texture2D( metalnessMap, vMetalnessMapUv );
	metalnessFactor *= texelMetalness.b;
#endif`,metalnessmap_pars_fragment:`#ifdef USE_METALNESSMAP
	uniform sampler2D metalnessMap;
#endif`,morphinstance_vertex:`#ifdef USE_INSTANCING_MORPH
	float morphTargetInfluences[ MORPHTARGETS_COUNT ];
	float morphTargetBaseInfluence = texelFetch( morphTexture, ivec2( 0, gl_InstanceID ), 0 ).r;
	for ( int i = 0; i < MORPHTARGETS_COUNT; i ++ ) {
		morphTargetInfluences[i] =  texelFetch( morphTexture, ivec2( i + 1, gl_InstanceID ), 0 ).r;
	}
#endif`,morphcolor_vertex:`#if defined( USE_MORPHCOLORS )
	vColor *= morphTargetBaseInfluence;
	for ( int i = 0; i < MORPHTARGETS_COUNT; i ++ ) {
		#if defined( USE_COLOR_ALPHA )
			if ( morphTargetInfluences[ i ] != 0.0 ) vColor += getMorph( gl_VertexID, i, 2 ) * morphTargetInfluences[ i ];
		#elif defined( USE_COLOR )
			if ( morphTargetInfluences[ i ] != 0.0 ) vColor += getMorph( gl_VertexID, i, 2 ).rgb * morphTargetInfluences[ i ];
		#endif
	}
#endif`,morphnormal_vertex:`#ifdef USE_MORPHNORMALS
	objectNormal *= morphTargetBaseInfluence;
	for ( int i = 0; i < MORPHTARGETS_COUNT; i ++ ) {
		if ( morphTargetInfluences[ i ] != 0.0 ) objectNormal += getMorph( gl_VertexID, i, 1 ).xyz * morphTargetInfluences[ i ];
	}
#endif`,morphtarget_pars_vertex:`#ifdef USE_MORPHTARGETS
	#ifndef USE_INSTANCING_MORPH
		uniform float morphTargetBaseInfluence;
		uniform float morphTargetInfluences[ MORPHTARGETS_COUNT ];
	#endif
	uniform sampler2DArray morphTargetsTexture;
	uniform ivec2 morphTargetsTextureSize;
	vec4 getMorph( const in int vertexIndex, const in int morphTargetIndex, const in int offset ) {
		int texelIndex = vertexIndex * MORPHTARGETS_TEXTURE_STRIDE + offset;
		int y = texelIndex / morphTargetsTextureSize.x;
		int x = texelIndex - y * morphTargetsTextureSize.x;
		ivec3 morphUV = ivec3( x, y, morphTargetIndex );
		return texelFetch( morphTargetsTexture, morphUV, 0 );
	}
#endif`,morphtarget_vertex:`#ifdef USE_MORPHTARGETS
	transformed *= morphTargetBaseInfluence;
	for ( int i = 0; i < MORPHTARGETS_COUNT; i ++ ) {
		if ( morphTargetInfluences[ i ] != 0.0 ) transformed += getMorph( gl_VertexID, i, 0 ).xyz * morphTargetInfluences[ i ];
	}
#endif`,normal_fragment_begin:`float faceDirection = gl_FrontFacing ? 1.0 : - 1.0;
#ifdef FLAT_SHADED
	vec3 fdx = dFdx( vViewPosition );
	vec3 fdy = dFdy( vViewPosition );
	vec3 normal = normalize( cross( fdx, fdy ) );
#else
	vec3 normal = normalize( vNormal );
	#ifdef DOUBLE_SIDED
		normal *= faceDirection;
	#endif
#endif
#if defined( USE_NORMALMAP_TANGENTSPACE ) || defined( USE_CLEARCOAT_NORMALMAP ) || defined( USE_ANISOTROPY )
	#ifdef USE_TANGENT
		mat3 tbn = mat3( normalize( vTangent ), normalize( vBitangent ), normal );
	#else
		mat3 tbn = getTangentFrame( - vViewPosition, normal,
		#if defined( USE_NORMALMAP )
			vNormalMapUv
		#elif defined( USE_CLEARCOAT_NORMALMAP )
			vClearcoatNormalMapUv
		#else
			vUv
		#endif
		);
	#endif
	#ifdef DOUBLE_SIDED
		tbn[0] *= faceDirection;
		tbn[1] *= faceDirection;
	#endif
#endif
#ifdef USE_CLEARCOAT_NORMALMAP
	#ifdef USE_TANGENT
		mat3 tbn2 = mat3( normalize( vTangent ), normalize( vBitangent ), normal );
	#else
		mat3 tbn2 = getTangentFrame( - vViewPosition, normal, vClearcoatNormalMapUv );
	#endif
	#ifdef DOUBLE_SIDED
		tbn2[0] *= faceDirection;
		tbn2[1] *= faceDirection;
	#endif
#endif
vec3 nonPerturbedNormal = normal;`,normal_fragment_maps:`#ifdef USE_NORMALMAP_OBJECTSPACE
	normal = texture2D( normalMap, vNormalMapUv ).xyz * 2.0 - 1.0;
	#ifdef FLIP_SIDED
		normal = - normal;
	#endif
	#ifdef DOUBLE_SIDED
		normal = normal * faceDirection;
	#endif
	normal = normalize( normalMatrix * normal );
#elif defined( USE_NORMALMAP_TANGENTSPACE )
	vec3 mapN = texture2D( normalMap, vNormalMapUv ).xyz * 2.0 - 1.0;
	#if defined( USE_PACKED_NORMALMAP )
		mapN = vec3( mapN.xy, sqrt( saturate( 1.0 - dot( mapN.xy, mapN.xy ) ) ) );
	#endif
	mapN.xy *= normalScale;
	normal = normalize( tbn * mapN );
#elif defined( USE_BUMPMAP )
	normal = perturbNormalArb( - vViewPosition, normal, dHdxy_fwd(), faceDirection );
#endif`,normal_pars_fragment:`#ifndef FLAT_SHADED
	varying vec3 vNormal;
	#ifdef USE_TANGENT
		varying vec3 vTangent;
		varying vec3 vBitangent;
	#endif
#endif`,normal_pars_vertex:`#ifndef FLAT_SHADED
	varying vec3 vNormal;
	#ifdef USE_TANGENT
		varying vec3 vTangent;
		varying vec3 vBitangent;
	#endif
#endif`,normal_vertex:`#ifndef FLAT_SHADED
	vNormal = normalize( transformedNormal );
	#ifdef USE_TANGENT
		vTangent = normalize( transformedTangent );
		vBitangent = normalize( cross( vNormal, vTangent ) * tangent.w );
		#ifdef FLIP_SIDED
			vBitangent = - vBitangent;
		#endif
	#endif
#endif`,normalmap_pars_fragment:`#ifdef USE_NORMALMAP
	uniform sampler2D normalMap;
	uniform vec2 normalScale;
#endif
#ifdef USE_NORMALMAP_OBJECTSPACE
	uniform mat3 normalMatrix;
#endif
#if ! defined ( USE_TANGENT ) && ( defined ( USE_NORMALMAP_TANGENTSPACE ) || defined ( USE_CLEARCOAT_NORMALMAP ) || defined( USE_ANISOTROPY ) )
	mat3 getTangentFrame( vec3 eye_pos, vec3 surf_norm, vec2 uv ) {
		vec3 q0 = dFdx( eye_pos.xyz );
		vec3 q1 = dFdy( eye_pos.xyz );
		vec2 st0 = dFdx( uv.st );
		vec2 st1 = dFdy( uv.st );
		vec3 N = surf_norm;
		vec3 q1perp = cross( q1, N );
		vec3 q0perp = cross( N, q0 );
		vec3 T = q1perp * st0.x + q0perp * st1.x;
		vec3 B = q1perp * st0.y + q0perp * st1.y;
		float det = max( dot( T, T ), dot( B, B ) );
		float scale = ( det == 0.0 ) ? 0.0 : inversesqrt( det );
		return mat3( T * scale, B * scale, N );
	}
#endif`,clearcoat_normal_fragment_begin:`#ifdef USE_CLEARCOAT
	vec3 clearcoatNormal = nonPerturbedNormal;
#endif`,clearcoat_normal_fragment_maps:`#ifdef USE_CLEARCOAT_NORMALMAP
	vec3 clearcoatMapN = texture2D( clearcoatNormalMap, vClearcoatNormalMapUv ).xyz * 2.0 - 1.0;
	clearcoatMapN.xy *= clearcoatNormalScale;
	clearcoatNormal = normalize( tbn2 * clearcoatMapN );
#endif`,clearcoat_pars_fragment:`#ifdef USE_CLEARCOATMAP
	uniform sampler2D clearcoatMap;
#endif
#ifdef USE_CLEARCOAT_NORMALMAP
	uniform sampler2D clearcoatNormalMap;
	uniform vec2 clearcoatNormalScale;
#endif
#ifdef USE_CLEARCOAT_ROUGHNESSMAP
	uniform sampler2D clearcoatRoughnessMap;
#endif`,iridescence_pars_fragment:`#ifdef USE_IRIDESCENCEMAP
	uniform sampler2D iridescenceMap;
#endif
#ifdef USE_IRIDESCENCE_THICKNESSMAP
	uniform sampler2D iridescenceThicknessMap;
#endif`,opaque_fragment:`#ifdef OPAQUE
diffuseColor.a = 1.0;
#endif
#ifdef USE_TRANSMISSION
diffuseColor.a *= material.transmissionAlpha;
#endif
gl_FragColor = vec4( outgoingLight, diffuseColor.a );`,packing:`vec3 packNormalToRGB( const in vec3 normal ) {
	return normalize( normal ) * 0.5 + 0.5;
}
vec3 unpackRGBToNormal( const in vec3 rgb ) {
	return 2.0 * rgb.xyz - 1.0;
}
const float PackUpscale = 256. / 255.;const float UnpackDownscale = 255. / 256.;const float ShiftRight8 = 1. / 256.;
const float Inv255 = 1. / 255.;
const vec4 PackFactors = vec4( 1.0, 256.0, 256.0 * 256.0, 256.0 * 256.0 * 256.0 );
const vec2 UnpackFactors2 = vec2( UnpackDownscale, 1.0 / PackFactors.g );
const vec3 UnpackFactors3 = vec3( UnpackDownscale / PackFactors.rg, 1.0 / PackFactors.b );
const vec4 UnpackFactors4 = vec4( UnpackDownscale / PackFactors.rgb, 1.0 / PackFactors.a );
vec4 packDepthToRGBA( const in float v ) {
	if( v <= 0.0 )
		return vec4( 0., 0., 0., 0. );
	if( v >= 1.0 )
		return vec4( 1., 1., 1., 1. );
	float vuf;
	float af = modf( v * PackFactors.a, vuf );
	float bf = modf( vuf * ShiftRight8, vuf );
	float gf = modf( vuf * ShiftRight8, vuf );
	return vec4( vuf * Inv255, gf * PackUpscale, bf * PackUpscale, af );
}
vec3 packDepthToRGB( const in float v ) {
	if( v <= 0.0 )
		return vec3( 0., 0., 0. );
	if( v >= 1.0 )
		return vec3( 1., 1., 1. );
	float vuf;
	float bf = modf( v * PackFactors.b, vuf );
	float gf = modf( vuf * ShiftRight8, vuf );
	return vec3( vuf * Inv255, gf * PackUpscale, bf );
}
vec2 packDepthToRG( const in float v ) {
	if( v <= 0.0 )
		return vec2( 0., 0. );
	if( v >= 1.0 )
		return vec2( 1., 1. );
	float vuf;
	float gf = modf( v * 256., vuf );
	return vec2( vuf * Inv255, gf );
}
float unpackRGBAToDepth( const in vec4 v ) {
	return dot( v, UnpackFactors4 );
}
float unpackRGBToDepth( const in vec3 v ) {
	return dot( v, UnpackFactors3 );
}
float unpackRGToDepth( const in vec2 v ) {
	return v.r * UnpackFactors2.r + v.g * UnpackFactors2.g;
}
vec4 pack2HalfToRGBA( const in vec2 v ) {
	vec4 r = vec4( v.x, fract( v.x * 255.0 ), v.y, fract( v.y * 255.0 ) );
	return vec4( r.x - r.y / 255.0, r.y, r.z - r.w / 255.0, r.w );
}
vec2 unpackRGBATo2Half( const in vec4 v ) {
	return vec2( v.x + ( v.y / 255.0 ), v.z + ( v.w / 255.0 ) );
}
float viewZToOrthographicDepth( const in float viewZ, const in float near, const in float far ) {
	return ( viewZ + near ) / ( near - far );
}
float orthographicDepthToViewZ( const in float depth, const in float near, const in float far ) {
	#ifdef USE_REVERSED_DEPTH_BUFFER
	
		return depth * ( far - near ) - far;
	#else
		return depth * ( near - far ) - near;
	#endif
}
float viewZToPerspectiveDepth( const in float viewZ, const in float near, const in float far ) {
	return ( ( near + viewZ ) * far ) / ( ( far - near ) * viewZ );
}
float perspectiveDepthToViewZ( const in float depth, const in float near, const in float far ) {
	
	#ifdef USE_REVERSED_DEPTH_BUFFER
		return ( near * far ) / ( ( near - far ) * depth - near );
	#else
		return ( near * far ) / ( ( far - near ) * depth - far );
	#endif
}`,premultiplied_alpha_fragment:`#ifdef PREMULTIPLIED_ALPHA
	gl_FragColor.rgb *= gl_FragColor.a;
#endif`,project_vertex:`vec4 mvPosition = vec4( transformed, 1.0 );
#ifdef USE_BATCHING
	mvPosition = batchingMatrix * mvPosition;
#endif
#ifdef USE_INSTANCING
	mvPosition = instanceMatrix * mvPosition;
#endif
mvPosition = modelViewMatrix * mvPosition;
gl_Position = projectionMatrix * mvPosition;`,dithering_fragment:`#ifdef DITHERING
	gl_FragColor.rgb = dithering( gl_FragColor.rgb );
#endif`,dithering_pars_fragment:`#ifdef DITHERING
	vec3 dithering( vec3 color ) {
		float grid_position = rand( gl_FragCoord.xy );
		vec3 dither_shift_RGB = vec3( 0.25 / 255.0, -0.25 / 255.0, 0.25 / 255.0 );
		dither_shift_RGB = mix( 2.0 * dither_shift_RGB, -2.0 * dither_shift_RGB, grid_position );
		return color + dither_shift_RGB;
	}
#endif`,roughnessmap_fragment:`float roughnessFactor = roughness;
#ifdef USE_ROUGHNESSMAP
	vec4 texelRoughness = texture2D( roughnessMap, vRoughnessMapUv );
	roughnessFactor *= texelRoughness.g;
#endif`,roughnessmap_pars_fragment:`#ifdef USE_ROUGHNESSMAP
	uniform sampler2D roughnessMap;
#endif`,shadowmap_pars_fragment:`#if NUM_SPOT_LIGHT_COORDS > 0
	varying vec4 vSpotLightCoord[ NUM_SPOT_LIGHT_COORDS ];
#endif
#if NUM_SPOT_LIGHT_MAPS > 0
	uniform sampler2D spotLightMap[ NUM_SPOT_LIGHT_MAPS ];
#endif
#ifdef USE_SHADOWMAP
	#if NUM_SUN_LIGHT_SHADOWS > 0
		#define SUN_LIGHT_CASCADES 2
		#if defined( SHADOWMAP_TYPE_PCF )
			uniform sampler2DShadow sunShadowMap[ NUM_SUN_LIGHT_SHADOWS ];
		#else
			uniform sampler2D sunShadowMap[ NUM_SUN_LIGHT_SHADOWS ];
		#endif
		uniform mat4 sunShadowMatrix[ NUM_SUN_LIGHT_SHADOWS * SUN_LIGHT_CASCADES ];
		uniform vec4 sunShadowCascade[ NUM_SUN_LIGHT_SHADOWS * SUN_LIGHT_CASCADES ];
		varying vec4 vSunShadowWorldPosition;
		varying vec3 vSunShadowWorldNormal;
		struct SunLightShadow {
			float shadowIntensity;
			float shadowBias;
			float shadowNormalBias;
			float shadowRadius;
			vec2 shadowMapSize;
		};
		uniform SunLightShadow sunLightShadows[ NUM_SUN_LIGHT_SHADOWS ];
	#endif
	#if NUM_DIR_LIGHT_SHADOWS > 0
		#if defined( SHADOWMAP_TYPE_PCF )
			uniform sampler2DShadow directionalShadowMap[ NUM_DIR_LIGHT_SHADOWS ];
		#else
			uniform sampler2D directionalShadowMap[ NUM_DIR_LIGHT_SHADOWS ];
		#endif
		varying vec4 vDirectionalShadowCoord[ NUM_DIR_LIGHT_SHADOWS ];
		struct DirectionalLightShadow {
			float shadowIntensity;
			float shadowBias;
			float shadowNormalBias;
			float shadowRadius;
			vec2 shadowMapSize;
		};
		uniform DirectionalLightShadow directionalLightShadows[ NUM_DIR_LIGHT_SHADOWS ];
	#endif
	#if NUM_SPOT_LIGHT_SHADOWS > 0
		#if defined( SHADOWMAP_TYPE_PCF )
			uniform sampler2DShadow spotShadowMap[ NUM_SPOT_LIGHT_SHADOWS ];
		#else
			uniform sampler2D spotShadowMap[ NUM_SPOT_LIGHT_SHADOWS ];
		#endif
		struct SpotLightShadow {
			float shadowIntensity;
			float shadowBias;
			float shadowNormalBias;
			float shadowRadius;
			vec2 shadowMapSize;
		};
		uniform SpotLightShadow spotLightShadows[ NUM_SPOT_LIGHT_SHADOWS ];
	#endif
	#if NUM_POINT_LIGHT_SHADOWS > 0
		#if defined( SHADOWMAP_TYPE_PCF )
			uniform samplerCubeShadow pointShadowMap[ NUM_POINT_LIGHT_SHADOWS ];
		#elif defined( SHADOWMAP_TYPE_BASIC )
			uniform samplerCube pointShadowMap[ NUM_POINT_LIGHT_SHADOWS ];
		#endif
		varying vec4 vPointShadowCoord[ NUM_POINT_LIGHT_SHADOWS ];
		struct PointLightShadow {
			float shadowIntensity;
			float shadowBias;
			float shadowNormalBias;
			float shadowRadius;
			vec2 shadowMapSize;
			float shadowCameraNear;
			float shadowCameraFar;
		};
		uniform PointLightShadow pointLightShadows[ NUM_POINT_LIGHT_SHADOWS ];
	#endif
	#if defined( SHADOWMAP_TYPE_PCF )
		float interleavedGradientNoise( vec2 position ) {
			return fract( 52.9829189 * fract( dot( position, vec2( 0.06711056, 0.00583715 ) ) ) );
		}
		vec2 vogelDiskSample( int sampleIndex, int samplesCount, float phi ) {
			const float goldenAngle = 2.399963229728653;
			float r = sqrt( ( float( sampleIndex ) + 0.5 ) / float( samplesCount ) );
			float theta = float( sampleIndex ) * goldenAngle + phi;
			return vec2( cos( theta ), sin( theta ) ) * r;
		}
	#endif
	#if defined( SHADOWMAP_TYPE_PCF )
		float getShadow( sampler2DShadow shadowMap, vec2 shadowMapSize, float shadowIntensity, float shadowBias, float shadowRadius, vec4 shadowCoord ) {
			float shadow = 1.0;
			shadowCoord.xyz /= shadowCoord.w;
			shadowCoord.z += shadowBias;
			bool inFrustum = shadowCoord.x >= 0.0 && shadowCoord.x <= 1.0 && shadowCoord.y >= 0.0 && shadowCoord.y <= 1.0;
			bool frustumTest = inFrustum && shadowCoord.z <= 1.0;
			if ( frustumTest ) {
				vec2 texelSize = vec2( 1.0 ) / shadowMapSize;
				float radius = shadowRadius * texelSize.x;
				float phi = interleavedGradientNoise( gl_FragCoord.xy ) * PI2;
				shadow = (
					texture( shadowMap, vec3( shadowCoord.xy + vogelDiskSample( 0, 5, phi ) * radius, shadowCoord.z ) ) +
					texture( shadowMap, vec3( shadowCoord.xy + vogelDiskSample( 1, 5, phi ) * radius, shadowCoord.z ) ) +
					texture( shadowMap, vec3( shadowCoord.xy + vogelDiskSample( 2, 5, phi ) * radius, shadowCoord.z ) ) +
					texture( shadowMap, vec3( shadowCoord.xy + vogelDiskSample( 3, 5, phi ) * radius, shadowCoord.z ) ) +
					texture( shadowMap, vec3( shadowCoord.xy + vogelDiskSample( 4, 5, phi ) * radius, shadowCoord.z ) )
				) * 0.2;
			}
			return mix( 1.0, shadow, shadowIntensity );
		}
	#elif defined( SHADOWMAP_TYPE_VSM )
		float getShadow( sampler2D shadowMap, vec2 shadowMapSize, float shadowIntensity, float shadowBias, float shadowRadius, vec4 shadowCoord ) {
			float shadow = 1.0;
			shadowCoord.xyz /= shadowCoord.w;
			#ifdef USE_REVERSED_DEPTH_BUFFER
				shadowCoord.z -= shadowBias;
			#else
				shadowCoord.z += shadowBias;
			#endif
			bool inFrustum = shadowCoord.x >= 0.0 && shadowCoord.x <= 1.0 && shadowCoord.y >= 0.0 && shadowCoord.y <= 1.0;
			bool frustumTest = inFrustum && shadowCoord.z <= 1.0;
			if ( frustumTest ) {
				vec2 distribution = texture2D( shadowMap, shadowCoord.xy ).rg;
				float mean = distribution.x;
				float variance = distribution.y * distribution.y;
				#ifdef USE_REVERSED_DEPTH_BUFFER
					float hard_shadow = step( mean, shadowCoord.z );
				#else
					float hard_shadow = step( shadowCoord.z, mean );
				#endif
				
				if ( hard_shadow == 1.0 ) {
					shadow = 1.0;
				} else {
					variance = max( variance, 0.0000001 );
					float d = shadowCoord.z - mean;
					float p_max = variance / ( variance + d * d );
					p_max = clamp( ( p_max - 0.3 ) / 0.65, 0.0, 1.0 );
					shadow = max( hard_shadow, p_max );
				}
			}
			return mix( 1.0, shadow, shadowIntensity );
		}
	#else
		float getShadow( sampler2D shadowMap, vec2 shadowMapSize, float shadowIntensity, float shadowBias, float shadowRadius, vec4 shadowCoord ) {
			float shadow = 1.0;
			shadowCoord.xyz /= shadowCoord.w;
			#ifdef USE_REVERSED_DEPTH_BUFFER
				shadowCoord.z -= shadowBias;
			#else
				shadowCoord.z += shadowBias;
			#endif
			bool inFrustum = shadowCoord.x >= 0.0 && shadowCoord.x <= 1.0 && shadowCoord.y >= 0.0 && shadowCoord.y <= 1.0;
			bool frustumTest = inFrustum && shadowCoord.z <= 1.0;
			if ( frustumTest ) {
				float depth = texture2D( shadowMap, shadowCoord.xy ).r;
				#ifdef USE_REVERSED_DEPTH_BUFFER
					shadow = step( depth, shadowCoord.z );
				#else
					shadow = step( shadowCoord.z, depth );
				#endif
			}
			return mix( 1.0, shadow, shadowIntensity );
		}
	#endif
	#if NUM_SUN_LIGHT_SHADOWS > 0
		float getSunShadow(
			#if defined( SHADOWMAP_TYPE_PCF )
				sampler2DShadow shadowMap,
			#else
				sampler2D shadowMap,
			#endif
			SunLightShadow sunLightShadow,
			int shadowIndex
		) {
			vec4 shadowWorldPosition = vec4( vSunShadowWorldPosition.xyz + vSunShadowWorldNormal * sunLightShadow.shadowNormalBias, 1.0 );
			float viewDepth = vSunShadowWorldPosition.w;
			int cascadeOffset = shadowIndex * SUN_LIGHT_CASCADES;
			float shadow = 1.0;
			for ( int i = SUN_LIGHT_CASCADES - 1; i >= 0; i -- ) {
				vec4 cascade = sunShadowCascade[ cascadeOffset + i ];
				if ( viewDepth >= cascade.x && viewDepth < cascade.y ) {
					float cascadeShadow = getShadow(
						shadowMap,
						sunLightShadow.shadowMapSize,
						sunLightShadow.shadowIntensity,
						sunLightShadow.shadowBias,
						sunLightShadow.shadowRadius,
						sunShadowMatrix[ cascadeOffset + i ] * shadowWorldPosition
					);
					shadow = mix( cascadeShadow, shadow, smoothstep( cascade.z, cascade.y, viewDepth ) );
				}
			}
			return shadow;
		}
	#endif
	#if NUM_POINT_LIGHT_SHADOWS > 0
	#if defined( SHADOWMAP_TYPE_PCF )
	float getPointShadow( samplerCubeShadow shadowMap, vec2 shadowMapSize, float shadowIntensity, float shadowBias, float shadowRadius, vec4 shadowCoord, float shadowCameraNear, float shadowCameraFar ) {
		float shadow = 1.0;
		vec3 lightToPosition = shadowCoord.xyz;
		vec3 bd3D = normalize( lightToPosition );
		vec3 absVec = abs( lightToPosition );
		float viewSpaceZ = max( max( absVec.x, absVec.y ), absVec.z );
		if ( viewSpaceZ - shadowCameraFar <= 0.0 && viewSpaceZ - shadowCameraNear >= 0.0 ) {
			#ifdef USE_REVERSED_DEPTH_BUFFER
				float dp = ( shadowCameraNear * ( shadowCameraFar - viewSpaceZ ) ) / ( viewSpaceZ * ( shadowCameraFar - shadowCameraNear ) );
				dp -= shadowBias;
			#else
				float dp = ( shadowCameraFar * ( viewSpaceZ - shadowCameraNear ) ) / ( viewSpaceZ * ( shadowCameraFar - shadowCameraNear ) );
				dp += shadowBias;
			#endif
			float texelSize = shadowRadius / shadowMapSize.x;
			vec3 absDir = abs( bd3D );
			vec3 tangent = absDir.x > absDir.z ? vec3( 0.0, 1.0, 0.0 ) : vec3( 1.0, 0.0, 0.0 );
			tangent = normalize( cross( bd3D, tangent ) );
			vec3 bitangent = cross( bd3D, tangent );
			float phi = interleavedGradientNoise( gl_FragCoord.xy ) * PI2;
			vec2 sample0 = vogelDiskSample( 0, 5, phi );
			vec2 sample1 = vogelDiskSample( 1, 5, phi );
			vec2 sample2 = vogelDiskSample( 2, 5, phi );
			vec2 sample3 = vogelDiskSample( 3, 5, phi );
			vec2 sample4 = vogelDiskSample( 4, 5, phi );
			shadow = (
				texture( shadowMap, vec4( bd3D + ( tangent * sample0.x + bitangent * sample0.y ) * texelSize, dp ) ) +
				texture( shadowMap, vec4( bd3D + ( tangent * sample1.x + bitangent * sample1.y ) * texelSize, dp ) ) +
				texture( shadowMap, vec4( bd3D + ( tangent * sample2.x + bitangent * sample2.y ) * texelSize, dp ) ) +
				texture( shadowMap, vec4( bd3D + ( tangent * sample3.x + bitangent * sample3.y ) * texelSize, dp ) ) +
				texture( shadowMap, vec4( bd3D + ( tangent * sample4.x + bitangent * sample4.y ) * texelSize, dp ) )
			) * 0.2;
		}
		return mix( 1.0, shadow, shadowIntensity );
	}
	#elif defined( SHADOWMAP_TYPE_BASIC )
	float getPointShadow( samplerCube shadowMap, vec2 shadowMapSize, float shadowIntensity, float shadowBias, float shadowRadius, vec4 shadowCoord, float shadowCameraNear, float shadowCameraFar ) {
		float shadow = 1.0;
		vec3 lightToPosition = shadowCoord.xyz;
		vec3 absVec = abs( lightToPosition );
		float viewSpaceZ = max( max( absVec.x, absVec.y ), absVec.z );
		if ( viewSpaceZ - shadowCameraFar <= 0.0 && viewSpaceZ - shadowCameraNear >= 0.0 ) {
			float dp = ( shadowCameraFar * ( viewSpaceZ - shadowCameraNear ) ) / ( viewSpaceZ * ( shadowCameraFar - shadowCameraNear ) );
			dp += shadowBias;
			vec3 bd3D = normalize( lightToPosition );
			float depth = textureCube( shadowMap, bd3D ).r;
			#ifdef USE_REVERSED_DEPTH_BUFFER
				depth = 1.0 - depth;
			#endif
			shadow = step( dp, depth );
		}
		return mix( 1.0, shadow, shadowIntensity );
	}
	#endif
	#endif
#endif`,shadowmap_pars_vertex:`#if NUM_SPOT_LIGHT_COORDS > 0
	uniform mat4 spotLightMatrix[ NUM_SPOT_LIGHT_COORDS ];
	varying vec4 vSpotLightCoord[ NUM_SPOT_LIGHT_COORDS ];
#endif
#ifdef USE_SHADOWMAP
	#if NUM_SUN_LIGHT_SHADOWS > 0
		varying vec4 vSunShadowWorldPosition;
		varying vec3 vSunShadowWorldNormal;
	#endif
	#if NUM_DIR_LIGHT_SHADOWS > 0
		uniform mat4 directionalShadowMatrix[ NUM_DIR_LIGHT_SHADOWS ];
		varying vec4 vDirectionalShadowCoord[ NUM_DIR_LIGHT_SHADOWS ];
		struct DirectionalLightShadow {
			float shadowIntensity;
			float shadowBias;
			float shadowNormalBias;
			float shadowRadius;
			vec2 shadowMapSize;
		};
		uniform DirectionalLightShadow directionalLightShadows[ NUM_DIR_LIGHT_SHADOWS ];
	#endif
	#if NUM_SPOT_LIGHT_SHADOWS > 0
		struct SpotLightShadow {
			float shadowIntensity;
			float shadowBias;
			float shadowNormalBias;
			float shadowRadius;
			vec2 shadowMapSize;
		};
		uniform SpotLightShadow spotLightShadows[ NUM_SPOT_LIGHT_SHADOWS ];
	#endif
	#if NUM_POINT_LIGHT_SHADOWS > 0
		uniform mat4 pointShadowMatrix[ NUM_POINT_LIGHT_SHADOWS ];
		varying vec4 vPointShadowCoord[ NUM_POINT_LIGHT_SHADOWS ];
		struct PointLightShadow {
			float shadowIntensity;
			float shadowBias;
			float shadowNormalBias;
			float shadowRadius;
			vec2 shadowMapSize;
			float shadowCameraNear;
			float shadowCameraFar;
		};
		uniform PointLightShadow pointLightShadows[ NUM_POINT_LIGHT_SHADOWS ];
	#endif
#endif`,shadowmap_vertex:`#if ( defined( USE_SHADOWMAP ) && ( NUM_DIR_LIGHT_SHADOWS > 0 || NUM_SUN_LIGHT_SHADOWS > 0 || NUM_POINT_LIGHT_SHADOWS > 0 ) ) || ( NUM_SPOT_LIGHT_COORDS > 0 )
	#ifdef HAS_NORMAL
		vec3 shadowWorldNormal = transformNormalByInverseViewMatrix( transformedNormal, viewMatrix );
	#else
		vec3 shadowWorldNormal = vec3( 0.0 );
	#endif
	vec4 shadowWorldPosition;
#endif
#if defined( USE_SHADOWMAP )
	#if NUM_SUN_LIGHT_SHADOWS > 0
		vSunShadowWorldPosition = vec4( worldPosition.xyz, - mvPosition.z );
		vSunShadowWorldNormal = shadowWorldNormal;
	#endif
	#if NUM_DIR_LIGHT_SHADOWS > 0
		#pragma unroll_loop_start
		for ( int i = 0; i < NUM_DIR_LIGHT_SHADOWS; i ++ ) {
			shadowWorldPosition = worldPosition + vec4( shadowWorldNormal * directionalLightShadows[ i ].shadowNormalBias, 0 );
			vDirectionalShadowCoord[ i ] = directionalShadowMatrix[ i ] * shadowWorldPosition;
		}
		#pragma unroll_loop_end
	#endif
	#if NUM_POINT_LIGHT_SHADOWS > 0
		#pragma unroll_loop_start
		for ( int i = 0; i < NUM_POINT_LIGHT_SHADOWS; i ++ ) {
			shadowWorldPosition = worldPosition + vec4( shadowWorldNormal * pointLightShadows[ i ].shadowNormalBias, 0 );
			vPointShadowCoord[ i ] = pointShadowMatrix[ i ] * shadowWorldPosition;
		}
		#pragma unroll_loop_end
	#endif
#endif
#if NUM_SPOT_LIGHT_COORDS > 0
	#pragma unroll_loop_start
	for ( int i = 0; i < NUM_SPOT_LIGHT_COORDS; i ++ ) {
		shadowWorldPosition = worldPosition;
		#if ( defined( USE_SHADOWMAP ) && UNROLLED_LOOP_INDEX < NUM_SPOT_LIGHT_SHADOWS )
			shadowWorldPosition.xyz += shadowWorldNormal * spotLightShadows[ i ].shadowNormalBias;
		#endif
		vSpotLightCoord[ i ] = spotLightMatrix[ i ] * shadowWorldPosition;
	}
	#pragma unroll_loop_end
#endif`,shadowmask_pars_fragment:`float getShadowMask() {
	float shadow = 1.0;
	#ifdef USE_SHADOWMAP
	#if NUM_SUN_LIGHT_SHADOWS > 0
	SunLightShadow sunLight;
	#pragma unroll_loop_start
	for ( int i = 0; i < NUM_SUN_LIGHT_SHADOWS; i ++ ) {
		sunLight = sunLightShadows[ i ];
		shadow *= receiveShadow ? getSunShadow( sunShadowMap[ i ], sunLight, UNROLLED_LOOP_INDEX ) : 1.0;
	}
	#pragma unroll_loop_end
	#endif
	#if NUM_DIR_LIGHT_SHADOWS > 0
	DirectionalLightShadow directionalLight;
	#pragma unroll_loop_start
	for ( int i = 0; i < NUM_DIR_LIGHT_SHADOWS; i ++ ) {
		directionalLight = directionalLightShadows[ i ];
		shadow *= receiveShadow ? getShadow( directionalShadowMap[ i ], directionalLight.shadowMapSize, directionalLight.shadowIntensity, directionalLight.shadowBias, directionalLight.shadowRadius, vDirectionalShadowCoord[ i ] ) : 1.0;
	}
	#pragma unroll_loop_end
	#endif
	#if NUM_SPOT_LIGHT_SHADOWS > 0
	SpotLightShadow spotLight;
	#pragma unroll_loop_start
	for ( int i = 0; i < NUM_SPOT_LIGHT_SHADOWS; i ++ ) {
		spotLight = spotLightShadows[ i ];
		shadow *= receiveShadow ? getShadow( spotShadowMap[ i ], spotLight.shadowMapSize, spotLight.shadowIntensity, spotLight.shadowBias, spotLight.shadowRadius, vSpotLightCoord[ i ] ) : 1.0;
	}
	#pragma unroll_loop_end
	#endif
	#if NUM_POINT_LIGHT_SHADOWS > 0 && ( defined( SHADOWMAP_TYPE_PCF ) || defined( SHADOWMAP_TYPE_BASIC ) )
	PointLightShadow pointLight;
	#pragma unroll_loop_start
	for ( int i = 0; i < NUM_POINT_LIGHT_SHADOWS; i ++ ) {
		pointLight = pointLightShadows[ i ];
		shadow *= receiveShadow ? getPointShadow( pointShadowMap[ i ], pointLight.shadowMapSize, pointLight.shadowIntensity, pointLight.shadowBias, pointLight.shadowRadius, vPointShadowCoord[ i ], pointLight.shadowCameraNear, pointLight.shadowCameraFar ) : 1.0;
	}
	#pragma unroll_loop_end
	#endif
	#endif
	return shadow;
}`,skinbase_vertex:`#ifdef USE_SKINNING
	mat4 boneMatX = getBoneMatrix( skinIndex.x );
	mat4 boneMatY = getBoneMatrix( skinIndex.y );
	mat4 boneMatZ = getBoneMatrix( skinIndex.z );
	mat4 boneMatW = getBoneMatrix( skinIndex.w );
#endif`,skinning_pars_vertex:`#ifdef USE_SKINNING
	uniform mat4 bindMatrix;
	uniform mat4 bindMatrixInverse;
	uniform highp sampler2D boneTexture;
	mat4 getBoneMatrix( const in float i ) {
		int size = textureSize( boneTexture, 0 ).x;
		int j = int( i ) * 4;
		int x = j % size;
		int y = j / size;
		vec4 v1 = texelFetch( boneTexture, ivec2( x, y ), 0 );
		vec4 v2 = texelFetch( boneTexture, ivec2( x + 1, y ), 0 );
		vec4 v3 = texelFetch( boneTexture, ivec2( x + 2, y ), 0 );
		vec4 v4 = texelFetch( boneTexture, ivec2( x + 3, y ), 0 );
		return mat4( v1, v2, v3, v4 );
	}
#endif`,skinning_vertex:`#ifdef USE_SKINNING
	vec4 skinVertex = bindMatrix * vec4( transformed, 1.0 );
	vec4 skinned = vec4( 0.0 );
	skinned += boneMatX * skinVertex * skinWeight.x;
	skinned += boneMatY * skinVertex * skinWeight.y;
	skinned += boneMatZ * skinVertex * skinWeight.z;
	skinned += boneMatW * skinVertex * skinWeight.w;
	transformed = ( bindMatrixInverse * skinned ).xyz;
#endif`,skinnormal_vertex:`#ifdef USE_SKINNING
	mat4 skinMatrix = mat4( 0.0 );
	skinMatrix += skinWeight.x * boneMatX;
	skinMatrix += skinWeight.y * boneMatY;
	skinMatrix += skinWeight.z * boneMatZ;
	skinMatrix += skinWeight.w * boneMatW;
	skinMatrix = bindMatrixInverse * skinMatrix * bindMatrix;
	objectNormal = vec4( skinMatrix * vec4( objectNormal, 0.0 ) ).xyz;
	#ifdef USE_TANGENT
		objectTangent = vec4( skinMatrix * vec4( objectTangent, 0.0 ) ).xyz;
	#endif
#endif`,specularmap_fragment:`float specularStrength;
#ifdef USE_SPECULARMAP
	vec4 texelSpecular = texture2D( specularMap, vSpecularMapUv );
	specularStrength = texelSpecular.r;
#else
	specularStrength = 1.0;
#endif`,specularmap_pars_fragment:`#ifdef USE_SPECULARMAP
	uniform sampler2D specularMap;
#endif`,tonemapping_fragment:`#if defined( TONE_MAPPING )
	gl_FragColor.rgb = toneMapping( gl_FragColor.rgb );
#endif`,tonemapping_pars_fragment:`#ifndef saturate
#define saturate( a ) clamp( a, 0.0, 1.0 )
#endif
uniform float toneMappingExposure;
vec3 LinearToneMapping( vec3 color ) {
	return saturate( toneMappingExposure * color );
}
vec3 ReinhardToneMapping( vec3 color ) {
	color *= toneMappingExposure;
	return saturate( color / ( vec3( 1.0 ) + color ) );
}
vec3 CineonToneMapping( vec3 color ) {
	color *= toneMappingExposure;
	color = max( vec3( 0.0 ), color - 0.004 );
	return pow( ( color * ( 6.2 * color + 0.5 ) ) / ( color * ( 6.2 * color + 1.7 ) + 0.06 ), vec3( 2.2 ) );
}
vec3 RRTAndODTFit( vec3 v ) {
	vec3 a = v * ( v + 0.0245786 ) - 0.000090537;
	vec3 b = v * ( 0.983729 * v + 0.4329510 ) + 0.238081;
	return a / b;
}
vec3 ACESFilmicToneMapping( vec3 color ) {
	const mat3 ACESInputMat = mat3(
		vec3( 0.59719, 0.07600, 0.02840 ),		vec3( 0.35458, 0.90834, 0.13383 ),
		vec3( 0.04823, 0.01566, 0.83777 )
	);
	const mat3 ACESOutputMat = mat3(
		vec3(  1.60475, -0.10208, -0.00327 ),		vec3( -0.53108,  1.10813, -0.07276 ),
		vec3( -0.07367, -0.00605,  1.07602 )
	);
	color *= toneMappingExposure / 0.6;
	color = ACESInputMat * color;
	color = RRTAndODTFit( color );
	color = ACESOutputMat * color;
	return saturate( color );
}
const mat3 LINEAR_REC2020_TO_LINEAR_SRGB = mat3(
	vec3( 1.6605, - 0.1246, - 0.0182 ),
	vec3( - 0.5876, 1.1329, - 0.1006 ),
	vec3( - 0.0728, - 0.0083, 1.1187 )
);
const mat3 LINEAR_SRGB_TO_LINEAR_REC2020 = mat3(
	vec3( 0.6274, 0.0691, 0.0164 ),
	vec3( 0.3293, 0.9195, 0.0880 ),
	vec3( 0.0433, 0.0113, 0.8956 )
);
vec3 agxDefaultContrastApprox( vec3 x ) {
	vec3 x2 = x * x;
	vec3 x4 = x2 * x2;
	return + 15.5 * x4 * x2
		- 40.14 * x4 * x
		+ 31.96 * x4
		- 6.868 * x2 * x
		+ 0.4298 * x2
		+ 0.1191 * x
		- 0.00232;
}
vec3 AgXToneMapping( vec3 color ) {
	const mat3 AgXInsetMatrix = mat3(
		vec3( 0.856627153315983, 0.137318972929847, 0.11189821299995 ),
		vec3( 0.0951212405381588, 0.761241990602591, 0.0767994186031903 ),
		vec3( 0.0482516061458583, 0.101439036467562, 0.811302368396859 )
	);
	const mat3 AgXOutsetMatrix = mat3(
		vec3( 1.1271005818144368, - 0.1413297634984383, - 0.14132976349843826 ),
		vec3( - 0.11060664309660323, 1.157823702216272, - 0.11060664309660294 ),
		vec3( - 0.016493938717834573, - 0.016493938717834257, 1.2519364065950405 )
	);
	const float AgxMinEv = - 12.47393;	const float AgxMaxEv = 4.026069;
	color *= toneMappingExposure;
	color = LINEAR_SRGB_TO_LINEAR_REC2020 * color;
	color = AgXInsetMatrix * color;
	color = max( color, 1e-10 );	color = log2( color );
	color = ( color - AgxMinEv ) / ( AgxMaxEv - AgxMinEv );
	color = clamp( color, 0.0, 1.0 );
	color = agxDefaultContrastApprox( color );
	color = AgXOutsetMatrix * color;
	color = pow( max( vec3( 0.0 ), color ), vec3( 2.2 ) );
	color = LINEAR_REC2020_TO_LINEAR_SRGB * color;
	color = clamp( color, 0.0, 1.0 );
	return color;
}
vec3 NeutralToneMapping( vec3 color ) {
	const float StartCompression = 0.8 - 0.04;
	const float Desaturation = 0.15;
	color *= toneMappingExposure;
	float x = min( color.r, min( color.g, color.b ) );
	float offset = x < 0.08 ? x - 6.25 * x * x : 0.04;
	color -= offset;
	float peak = max( color.r, max( color.g, color.b ) );
	if ( peak < StartCompression ) return color;
	float d = 1. - StartCompression;
	float newPeak = 1. - d * d / ( peak + d - StartCompression );
	color *= newPeak / peak;
	float g = 1. - 1. / ( Desaturation * ( peak - newPeak ) + 1. );
	return mix( color, vec3( newPeak ), g );
}
vec3 CustomToneMapping( vec3 color ) { return color; }`,transmission_fragment:`#ifdef USE_TRANSMISSION
	material.transmission = transmission;
	material.transmissionAlpha = 1.0;
	material.thickness = thickness;
	material.attenuationDistance = attenuationDistance;
	material.attenuationColor = attenuationColor;
	#ifdef USE_TRANSMISSIONMAP
		material.transmission *= texture2D( transmissionMap, vTransmissionMapUv ).r;
	#endif
	#ifdef USE_THICKNESSMAP
		material.thickness *= texture2D( thicknessMap, vThicknessMapUv ).g;
	#endif
	vec3 pos = vWorldPosition;
	vec3 v = normalize( cameraPosition - pos );
	vec3 n = transformNormalByInverseViewMatrix( normal, viewMatrix );
	vec4 transmitted = getIBLVolumeRefraction(
		n, v, material.roughness, material.diffuseContribution, material.specularColorBlended, material.specularF90,
		pos, modelMatrix, viewMatrix, projectionMatrix, material.dispersion, material.ior, material.thickness,
		material.attenuationColor, material.attenuationDistance );
	material.transmissionAlpha = mix( material.transmissionAlpha, transmitted.a, material.transmission );
	totalDiffuse = mix( totalDiffuse, transmitted.rgb, material.transmission );
#endif`,transmission_pars_fragment:`#ifdef USE_TRANSMISSION
	uniform float transmission;
	uniform float thickness;
	uniform float attenuationDistance;
	uniform vec3 attenuationColor;
	#ifdef USE_TRANSMISSIONMAP
		uniform sampler2D transmissionMap;
	#endif
	#ifdef USE_THICKNESSMAP
		uniform sampler2D thicknessMap;
	#endif
	uniform vec2 transmissionSamplerSize;
	uniform sampler2D transmissionSamplerMap;
	uniform mat4 modelMatrix;
	uniform mat4 projectionMatrix;
	varying vec3 vWorldPosition;
	float w0( float a ) {
		return ( 1.0 / 6.0 ) * ( a * ( a * ( - a + 3.0 ) - 3.0 ) + 1.0 );
	}
	float w1( float a ) {
		return ( 1.0 / 6.0 ) * ( a *  a * ( 3.0 * a - 6.0 ) + 4.0 );
	}
	float w2( float a ){
		return ( 1.0 / 6.0 ) * ( a * ( a * ( - 3.0 * a + 3.0 ) + 3.0 ) + 1.0 );
	}
	float w3( float a ) {
		return ( 1.0 / 6.0 ) * ( a * a * a );
	}
	float g0( float a ) {
		return w0( a ) + w1( a );
	}
	float g1( float a ) {
		return w2( a ) + w3( a );
	}
	float h0( float a ) {
		return - 1.0 + w1( a ) / ( w0( a ) + w1( a ) );
	}
	float h1( float a ) {
		return 1.0 + w3( a ) / ( w2( a ) + w3( a ) );
	}
	vec4 bicubic( sampler2D tex, vec2 uv, vec4 texelSize, float lod ) {
		uv = uv * texelSize.zw + 0.5;
		vec2 iuv = floor( uv );
		vec2 fuv = fract( uv );
		float g0x = g0( fuv.x );
		float g1x = g1( fuv.x );
		float h0x = h0( fuv.x );
		float h1x = h1( fuv.x );
		float h0y = h0( fuv.y );
		float h1y = h1( fuv.y );
		vec2 p0 = ( vec2( iuv.x + h0x, iuv.y + h0y ) - 0.5 ) * texelSize.xy;
		vec2 p1 = ( vec2( iuv.x + h1x, iuv.y + h0y ) - 0.5 ) * texelSize.xy;
		vec2 p2 = ( vec2( iuv.x + h0x, iuv.y + h1y ) - 0.5 ) * texelSize.xy;
		vec2 p3 = ( vec2( iuv.x + h1x, iuv.y + h1y ) - 0.5 ) * texelSize.xy;
		return g0( fuv.y ) * ( g0x * textureLod( tex, p0, lod ) + g1x * textureLod( tex, p1, lod ) ) +
			g1( fuv.y ) * ( g0x * textureLod( tex, p2, lod ) + g1x * textureLod( tex, p3, lod ) );
	}
	vec4 textureBicubic( sampler2D sampler, vec2 uv, float lod ) {
		vec2 fLodSize = vec2( textureSize( sampler, int( lod ) ) );
		vec2 cLodSize = vec2( textureSize( sampler, int( lod + 1.0 ) ) );
		vec2 fLodSizeInv = 1.0 / fLodSize;
		vec2 cLodSizeInv = 1.0 / cLodSize;
		vec4 fSample = bicubic( sampler, uv, vec4( fLodSizeInv, fLodSize ), floor( lod ) );
		vec4 cSample = bicubic( sampler, uv, vec4( cLodSizeInv, cLodSize ), ceil( lod ) );
		return mix( fSample, cSample, fract( lod ) );
	}
	vec3 getVolumeTransmissionRay( const in vec3 n, const in vec3 v, const in float thickness, const in float ior, const in mat4 modelMatrix ) {
		vec3 refractionVector = refract( - v, normalize( n ), 1.0 / ior );
		vec3 modelScale;
		modelScale.x = length( vec3( modelMatrix[ 0 ].xyz ) );
		modelScale.y = length( vec3( modelMatrix[ 1 ].xyz ) );
		modelScale.z = length( vec3( modelMatrix[ 2 ].xyz ) );
		return normalize( refractionVector ) * thickness * modelScale;
	}
	float applyIorToRoughness( const in float roughness, const in float ior ) {
		return roughness * clamp( ior * 2.0 - 2.0, 0.0, 1.0 );
	}
	vec4 getTransmissionSample( const in vec2 fragCoord, const in float roughness, const in float ior ) {
		float lod = log2( transmissionSamplerSize.x ) * applyIorToRoughness( roughness, ior );
		return textureBicubic( transmissionSamplerMap, fragCoord.xy, lod );
	}
	vec3 volumeAttenuation( const in float transmissionDistance, const in vec3 attenuationColor, const in float attenuationDistance ) {
		if ( isinf( attenuationDistance ) ) {
			return vec3( 1.0 );
		} else {
			vec3 attenuationCoefficient = -log( attenuationColor ) / attenuationDistance;
			vec3 transmittance = exp( - attenuationCoefficient * transmissionDistance );			return transmittance;
		}
	}
	vec4 getIBLVolumeRefraction( const in vec3 n, const in vec3 v, const in float roughness, const in vec3 diffuseColor,
		const in vec3 specularColor, const in float specularF90, const in vec3 position, const in mat4 modelMatrix,
		const in mat4 viewMatrix, const in mat4 projMatrix, const in float dispersion, const in float ior, const in float thickness,
		const in vec3 attenuationColor, const in float attenuationDistance ) {
		vec4 transmittedLight;
		vec3 transmittance;
		#ifdef USE_DISPERSION
			float halfSpread = ( ior - 1.0 ) * 0.025 * dispersion;
			vec3 iors = vec3( ior - halfSpread, ior, ior + halfSpread );
			for ( int i = 0; i < 3; i ++ ) {
				vec3 transmissionRay = getVolumeTransmissionRay( n, v, thickness, iors[ i ], modelMatrix );
				vec3 refractedRayExit = position + transmissionRay;
				vec4 ndcPos = projMatrix * viewMatrix * vec4( refractedRayExit, 1.0 );
				vec2 refractionCoords = ndcPos.xy / ndcPos.w;
				refractionCoords += 1.0;
				refractionCoords /= 2.0;
				vec4 transmissionSample = getTransmissionSample( refractionCoords, roughness, iors[ i ] );
				transmittedLight[ i ] = transmissionSample[ i ];
				transmittedLight.a += transmissionSample.a;
				transmittance[ i ] = diffuseColor[ i ] * volumeAttenuation( length( transmissionRay ), attenuationColor, attenuationDistance )[ i ];
			}
			transmittedLight.a /= 3.0;
		#else
			vec3 transmissionRay = getVolumeTransmissionRay( n, v, thickness, ior, modelMatrix );
			vec3 refractedRayExit = position + transmissionRay;
			vec4 ndcPos = projMatrix * viewMatrix * vec4( refractedRayExit, 1.0 );
			vec2 refractionCoords = ndcPos.xy / ndcPos.w;
			refractionCoords += 1.0;
			refractionCoords /= 2.0;
			transmittedLight = getTransmissionSample( refractionCoords, roughness, ior );
			transmittance = diffuseColor * volumeAttenuation( length( transmissionRay ), attenuationColor, attenuationDistance );
		#endif
		vec3 attenuatedColor = transmittance * transmittedLight.rgb;
		vec3 F = EnvironmentBRDF( n, v, specularColor, specularF90, roughness );
		float transmittanceFactor = ( transmittance.r + transmittance.g + transmittance.b ) / 3.0;
		return vec4( ( 1.0 - F ) * attenuatedColor, 1.0 - ( 1.0 - transmittedLight.a ) * transmittanceFactor );
	}
#endif`,uv_pars_fragment:`#if defined( USE_UV ) || defined( USE_ANISOTROPY )
	varying vec2 vUv;
#endif
#ifdef USE_MAP
	varying vec2 vMapUv;
#endif
#ifdef USE_ALPHAMAP
	varying vec2 vAlphaMapUv;
#endif
#ifdef USE_LIGHTMAP
	varying vec2 vLightMapUv;
#endif
#ifdef USE_AOMAP
	varying vec2 vAoMapUv;
#endif
#ifdef USE_BUMPMAP
	varying vec2 vBumpMapUv;
#endif
#ifdef USE_NORMALMAP
	varying vec2 vNormalMapUv;
#endif
#ifdef USE_EMISSIVEMAP
	varying vec2 vEmissiveMapUv;
#endif
#ifdef USE_METALNESSMAP
	varying vec2 vMetalnessMapUv;
#endif
#ifdef USE_ROUGHNESSMAP
	varying vec2 vRoughnessMapUv;
#endif
#ifdef USE_ANISOTROPYMAP
	varying vec2 vAnisotropyMapUv;
#endif
#ifdef USE_CLEARCOATMAP
	varying vec2 vClearcoatMapUv;
#endif
#ifdef USE_CLEARCOAT_NORMALMAP
	varying vec2 vClearcoatNormalMapUv;
#endif
#ifdef USE_CLEARCOAT_ROUGHNESSMAP
	varying vec2 vClearcoatRoughnessMapUv;
#endif
#ifdef USE_IRIDESCENCEMAP
	varying vec2 vIridescenceMapUv;
#endif
#ifdef USE_IRIDESCENCE_THICKNESSMAP
	varying vec2 vIridescenceThicknessMapUv;
#endif
#ifdef USE_SHEEN_COLORMAP
	varying vec2 vSheenColorMapUv;
#endif
#ifdef USE_SHEEN_ROUGHNESSMAP
	varying vec2 vSheenRoughnessMapUv;
#endif
#ifdef USE_SPECULARMAP
	varying vec2 vSpecularMapUv;
#endif
#ifdef USE_SPECULAR_COLORMAP
	varying vec2 vSpecularColorMapUv;
#endif
#ifdef USE_SPECULAR_INTENSITYMAP
	varying vec2 vSpecularIntensityMapUv;
#endif
#ifdef USE_TRANSMISSIONMAP
	uniform mat3 transmissionMapTransform;
	varying vec2 vTransmissionMapUv;
#endif
#ifdef USE_THICKNESSMAP
	uniform mat3 thicknessMapTransform;
	varying vec2 vThicknessMapUv;
#endif`,uv_pars_vertex:`#if defined( USE_UV ) || defined( USE_ANISOTROPY )
	varying vec2 vUv;
#endif
#ifdef USE_MAP
	uniform mat3 mapTransform;
	varying vec2 vMapUv;
#endif
#ifdef USE_ALPHAMAP
	uniform mat3 alphaMapTransform;
	varying vec2 vAlphaMapUv;
#endif
#ifdef USE_LIGHTMAP
	uniform mat3 lightMapTransform;
	varying vec2 vLightMapUv;
#endif
#ifdef USE_AOMAP
	uniform mat3 aoMapTransform;
	varying vec2 vAoMapUv;
#endif
#ifdef USE_BUMPMAP
	uniform mat3 bumpMapTransform;
	varying vec2 vBumpMapUv;
#endif
#ifdef USE_NORMALMAP
	uniform mat3 normalMapTransform;
	varying vec2 vNormalMapUv;
#endif
#ifdef USE_DISPLACEMENTMAP
	uniform mat3 displacementMapTransform;
	varying vec2 vDisplacementMapUv;
#endif
#ifdef USE_EMISSIVEMAP
	uniform mat3 emissiveMapTransform;
	varying vec2 vEmissiveMapUv;
#endif
#ifdef USE_METALNESSMAP
	uniform mat3 metalnessMapTransform;
	varying vec2 vMetalnessMapUv;
#endif
#ifdef USE_ROUGHNESSMAP
	uniform mat3 roughnessMapTransform;
	varying vec2 vRoughnessMapUv;
#endif
#ifdef USE_ANISOTROPYMAP
	uniform mat3 anisotropyMapTransform;
	varying vec2 vAnisotropyMapUv;
#endif
#ifdef USE_CLEARCOATMAP
	uniform mat3 clearcoatMapTransform;
	varying vec2 vClearcoatMapUv;
#endif
#ifdef USE_CLEARCOAT_NORMALMAP
	uniform mat3 clearcoatNormalMapTransform;
	varying vec2 vClearcoatNormalMapUv;
#endif
#ifdef USE_CLEARCOAT_ROUGHNESSMAP
	uniform mat3 clearcoatRoughnessMapTransform;
	varying vec2 vClearcoatRoughnessMapUv;
#endif
#ifdef USE_SHEEN_COLORMAP
	uniform mat3 sheenColorMapTransform;
	varying vec2 vSheenColorMapUv;
#endif
#ifdef USE_SHEEN_ROUGHNESSMAP
	uniform mat3 sheenRoughnessMapTransform;
	varying vec2 vSheenRoughnessMapUv;
#endif
#ifdef USE_IRIDESCENCEMAP
	uniform mat3 iridescenceMapTransform;
	varying vec2 vIridescenceMapUv;
#endif
#ifdef USE_IRIDESCENCE_THICKNESSMAP
	uniform mat3 iridescenceThicknessMapTransform;
	varying vec2 vIridescenceThicknessMapUv;
#endif
#ifdef USE_SPECULARMAP
	uniform mat3 specularMapTransform;
	varying vec2 vSpecularMapUv;
#endif
#ifdef USE_SPECULAR_COLORMAP
	uniform mat3 specularColorMapTransform;
	varying vec2 vSpecularColorMapUv;
#endif
#ifdef USE_SPECULAR_INTENSITYMAP
	uniform mat3 specularIntensityMapTransform;
	varying vec2 vSpecularIntensityMapUv;
#endif
#ifdef USE_TRANSMISSIONMAP
	uniform mat3 transmissionMapTransform;
	varying vec2 vTransmissionMapUv;
#endif
#ifdef USE_THICKNESSMAP
	uniform mat3 thicknessMapTransform;
	varying vec2 vThicknessMapUv;
#endif`,uv_vertex:`#if defined( USE_UV ) || defined( USE_ANISOTROPY )
	vUv = vec3( uv, 1 ).xy;
#endif
#ifdef USE_MAP
	vMapUv = ( mapTransform * vec3( MAP_UV, 1 ) ).xy;
#endif
#ifdef USE_ALPHAMAP
	vAlphaMapUv = ( alphaMapTransform * vec3( ALPHAMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_LIGHTMAP
	vLightMapUv = ( lightMapTransform * vec3( LIGHTMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_AOMAP
	vAoMapUv = ( aoMapTransform * vec3( AOMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_BUMPMAP
	vBumpMapUv = ( bumpMapTransform * vec3( BUMPMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_NORMALMAP
	vNormalMapUv = ( normalMapTransform * vec3( NORMALMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_DISPLACEMENTMAP
	vDisplacementMapUv = ( displacementMapTransform * vec3( DISPLACEMENTMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_EMISSIVEMAP
	vEmissiveMapUv = ( emissiveMapTransform * vec3( EMISSIVEMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_METALNESSMAP
	vMetalnessMapUv = ( metalnessMapTransform * vec3( METALNESSMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_ROUGHNESSMAP
	vRoughnessMapUv = ( roughnessMapTransform * vec3( ROUGHNESSMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_ANISOTROPYMAP
	vAnisotropyMapUv = ( anisotropyMapTransform * vec3( ANISOTROPYMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_CLEARCOATMAP
	vClearcoatMapUv = ( clearcoatMapTransform * vec3( CLEARCOATMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_CLEARCOAT_NORMALMAP
	vClearcoatNormalMapUv = ( clearcoatNormalMapTransform * vec3( CLEARCOAT_NORMALMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_CLEARCOAT_ROUGHNESSMAP
	vClearcoatRoughnessMapUv = ( clearcoatRoughnessMapTransform * vec3( CLEARCOAT_ROUGHNESSMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_IRIDESCENCEMAP
	vIridescenceMapUv = ( iridescenceMapTransform * vec3( IRIDESCENCEMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_IRIDESCENCE_THICKNESSMAP
	vIridescenceThicknessMapUv = ( iridescenceThicknessMapTransform * vec3( IRIDESCENCE_THICKNESSMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_SHEEN_COLORMAP
	vSheenColorMapUv = ( sheenColorMapTransform * vec3( SHEEN_COLORMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_SHEEN_ROUGHNESSMAP
	vSheenRoughnessMapUv = ( sheenRoughnessMapTransform * vec3( SHEEN_ROUGHNESSMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_SPECULARMAP
	vSpecularMapUv = ( specularMapTransform * vec3( SPECULARMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_SPECULAR_COLORMAP
	vSpecularColorMapUv = ( specularColorMapTransform * vec3( SPECULAR_COLORMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_SPECULAR_INTENSITYMAP
	vSpecularIntensityMapUv = ( specularIntensityMapTransform * vec3( SPECULAR_INTENSITYMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_TRANSMISSIONMAP
	vTransmissionMapUv = ( transmissionMapTransform * vec3( TRANSMISSIONMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_THICKNESSMAP
	vThicknessMapUv = ( thicknessMapTransform * vec3( THICKNESSMAP_UV, 1 ) ).xy;
#endif`,worldpos_vertex:`#if defined( USE_ENVMAP ) || defined( DISTANCE ) || defined ( USE_SHADOWMAP ) || defined ( USE_TRANSMISSION ) || NUM_SPOT_LIGHT_COORDS > 0
	vec4 worldPosition = vec4( transformed, 1.0 );
	#ifdef USE_BATCHING
		worldPosition = batchingMatrix * worldPosition;
	#endif
	#ifdef USE_INSTANCING
		worldPosition = instanceMatrix * worldPosition;
	#endif
	worldPosition = modelMatrix * worldPosition;
#endif`,background_vert:`varying vec2 vUv;
uniform mat3 uvTransform;
void main() {
	vUv = ( uvTransform * vec3( uv, 1 ) ).xy;
	gl_Position = vec4( position.xy, 1.0, 1.0 );
}`,background_frag:`uniform sampler2D t2D;
uniform float backgroundIntensity;
varying vec2 vUv;
void main() {
	vec4 texColor = texture2D( t2D, vUv );
	#ifdef DECODE_VIDEO_TEXTURE
		texColor = vec4( mix( pow( texColor.rgb * 0.9478672986 + vec3( 0.0521327014 ), vec3( 2.4 ) ), texColor.rgb * 0.0773993808, vec3( lessThanEqual( texColor.rgb, vec3( 0.04045 ) ) ) ), texColor.w );
	#endif
	texColor.rgb *= backgroundIntensity;
	gl_FragColor = texColor;
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
}`,backgroundCube_vert:`varying vec3 vWorldDirection;
#include <common>
void main() {
	vWorldDirection = transformDirection( position, modelMatrix );
	#include <begin_vertex>
	#include <project_vertex>
	gl_Position.z = gl_Position.w;
}`,backgroundCube_frag:`#ifdef ENVMAP_TYPE_CUBE
	uniform samplerCube envMap;
#elif defined( ENVMAP_TYPE_CUBE_UV )
	uniform sampler2D envMap;
#endif
uniform float backgroundBlurriness;
uniform float backgroundIntensity;
uniform mat3 backgroundRotation;
varying vec3 vWorldDirection;
#include <cube_uv_reflection_fragment>
void main() {
	#ifdef ENVMAP_TYPE_CUBE
		vec4 texColor = textureCube( envMap, backgroundRotation * vWorldDirection );
	#elif defined( ENVMAP_TYPE_CUBE_UV )
		vec4 texColor = textureCubeUV( envMap, backgroundRotation * vWorldDirection, backgroundBlurriness );
	#else
		vec4 texColor = vec4( 0.0, 0.0, 0.0, 1.0 );
	#endif
	texColor.rgb *= backgroundIntensity;
	gl_FragColor = texColor;
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
}`,cube_vert:`varying vec3 vWorldDirection;
#include <common>
void main() {
	vWorldDirection = transformDirection( position, modelMatrix );
	#include <begin_vertex>
	#include <project_vertex>
	gl_Position.z = gl_Position.w;
}`,cube_frag:`uniform samplerCube tCube;
uniform float tFlip;
uniform float opacity;
varying vec3 vWorldDirection;
void main() {
	vec4 texColor = textureCube( tCube, vec3( tFlip * vWorldDirection.x, vWorldDirection.yz ) );
	gl_FragColor = texColor;
	gl_FragColor.a *= opacity;
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
}`,depth_vert:`#include <common>
#include <batching_pars_vertex>
#include <uv_pars_vertex>
#include <displacementmap_pars_vertex>
#include <morphtarget_pars_vertex>
#include <skinning_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
varying vec2 vHighPrecisionZW;
void main() {
	#include <uv_vertex>
	#include <batching_vertex>
	#include <skinbase_vertex>
	#include <morphinstance_vertex>
	#ifdef USE_DISPLACEMENTMAP
		#include <beginnormal_vertex>
		#include <morphnormal_vertex>
		#include <skinnormal_vertex>
	#endif
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <skinning_vertex>
	#include <displacementmap_vertex>
	#include <project_vertex>
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
	vHighPrecisionZW = gl_Position.zw;
}`,depth_frag:`#if DEPTH_PACKING == 3200
	uniform float opacity;
#endif
#include <common>
#include <packing>
#include <uv_pars_fragment>
#include <map_pars_fragment>
#include <alphamap_pars_fragment>
#include <alphatest_pars_fragment>
#include <alphahash_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
varying vec2 vHighPrecisionZW;
void main() {
	vec4 diffuseColor = vec4( 1.0 );
	#include <clipping_planes_fragment>
	#if DEPTH_PACKING == 3200
		diffuseColor.a = opacity;
	#endif
	#include <map_fragment>
	#include <alphamap_fragment>
	#include <alphatest_fragment>
	#include <alphahash_fragment>
	#include <logdepthbuf_fragment>
	#ifdef USE_REVERSED_DEPTH_BUFFER
		float fragCoordZ = vHighPrecisionZW[ 0 ] / vHighPrecisionZW[ 1 ];
	#else
		float fragCoordZ = 0.5 * vHighPrecisionZW[ 0 ] / vHighPrecisionZW[ 1 ] + 0.5;
	#endif
	#if DEPTH_PACKING == 3200
		gl_FragColor = vec4( vec3( 1.0 - fragCoordZ ), opacity );
	#elif DEPTH_PACKING == 3201
		gl_FragColor = packDepthToRGBA( fragCoordZ );
	#elif DEPTH_PACKING == 3202
		gl_FragColor = vec4( packDepthToRGB( fragCoordZ ), 1.0 );
	#elif DEPTH_PACKING == 3203
		gl_FragColor = vec4( packDepthToRG( fragCoordZ ), 0.0, 1.0 );
	#endif
}`,distance_vert:`#define DISTANCE
varying vec3 vWorldPosition;
#include <common>
#include <batching_pars_vertex>
#include <uv_pars_vertex>
#include <displacementmap_pars_vertex>
#include <morphtarget_pars_vertex>
#include <skinning_pars_vertex>
#include <clipping_planes_pars_vertex>
void main() {
	#include <uv_vertex>
	#include <batching_vertex>
	#include <skinbase_vertex>
	#include <morphinstance_vertex>
	#ifdef USE_DISPLACEMENTMAP
		#include <beginnormal_vertex>
		#include <morphnormal_vertex>
		#include <skinnormal_vertex>
	#endif
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <skinning_vertex>
	#include <displacementmap_vertex>
	#include <project_vertex>
	#include <worldpos_vertex>
	#include <clipping_planes_vertex>
	vWorldPosition = worldPosition.xyz;
}`,distance_frag:`#define DISTANCE
uniform vec3 referencePosition;
uniform float nearDistance;
uniform float farDistance;
varying vec3 vWorldPosition;
#include <common>
#include <uv_pars_fragment>
#include <map_pars_fragment>
#include <alphamap_pars_fragment>
#include <alphatest_pars_fragment>
#include <alphahash_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( 1.0 );
	#include <clipping_planes_fragment>
	#include <map_fragment>
	#include <alphamap_fragment>
	#include <alphatest_fragment>
	#include <alphahash_fragment>
	float dist = length( vWorldPosition - referencePosition );
	dist = ( dist - nearDistance ) / ( farDistance - nearDistance );
	dist = saturate( dist );
	gl_FragColor = vec4( dist, 0.0, 0.0, 1.0 );
}`,equirect_vert:`varying vec3 vWorldDirection;
#include <common>
void main() {
	vWorldDirection = transformDirection( position, modelMatrix );
	#include <begin_vertex>
	#include <project_vertex>
}`,equirect_frag:`uniform sampler2D tEquirect;
varying vec3 vWorldDirection;
#include <common>
void main() {
	vec3 direction = normalize( vWorldDirection );
	vec2 sampleUV = equirectUv( direction );
	gl_FragColor = texture2D( tEquirect, sampleUV );
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
}`,linedashed_vert:`uniform float scale;
attribute float lineDistance;
varying float vLineDistance;
#include <common>
#include <uv_pars_vertex>
#include <color_pars_vertex>
#include <fog_pars_vertex>
#include <morphtarget_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
void main() {
	vLineDistance = scale * lineDistance;
	#include <uv_vertex>
	#include <color_vertex>
	#include <morphinstance_vertex>
	#include <morphcolor_vertex>
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <project_vertex>
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
	#include <fog_vertex>
}`,linedashed_frag:`uniform vec3 diffuse;
uniform float opacity;
uniform float dashSize;
uniform float totalSize;
varying float vLineDistance;
#include <common>
#include <color_pars_fragment>
#include <uv_pars_fragment>
#include <map_pars_fragment>
#include <fog_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( diffuse, opacity );
	#include <clipping_planes_fragment>
	if ( mod( vLineDistance, totalSize ) > dashSize ) {
		discard;
	}
	vec3 outgoingLight = vec3( 0.0 );
	#include <logdepthbuf_fragment>
	#include <map_fragment>
	#include <color_fragment>
	outgoingLight = diffuseColor.rgb;
	#include <opaque_fragment>
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
	#include <fog_fragment>
	#include <premultiplied_alpha_fragment>
}`,meshbasic_vert:`#include <common>
#include <batching_pars_vertex>
#include <uv_pars_vertex>
#include <envmap_pars_vertex>
#include <color_pars_vertex>
#include <fog_pars_vertex>
#include <morphtarget_pars_vertex>
#include <skinning_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
void main() {
	#include <uv_vertex>
	#include <color_vertex>
	#include <morphinstance_vertex>
	#include <morphcolor_vertex>
	#include <batching_vertex>
	#if defined ( USE_ENVMAP ) || defined ( USE_SKINNING )
		#include <beginnormal_vertex>
		#include <morphnormal_vertex>
		#include <skinbase_vertex>
		#include <skinnormal_vertex>
		#include <defaultnormal_vertex>
	#endif
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <skinning_vertex>
	#include <project_vertex>
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
	#include <worldpos_vertex>
	#include <envmap_vertex>
	#include <fog_vertex>
}`,meshbasic_frag:`uniform vec3 diffuse;
uniform float opacity;
#ifndef FLAT_SHADED
	varying vec3 vNormal;
#endif
#include <common>
#include <dithering_pars_fragment>
#include <color_pars_fragment>
#include <uv_pars_fragment>
#include <map_pars_fragment>
#include <alphamap_pars_fragment>
#include <alphatest_pars_fragment>
#include <alphahash_pars_fragment>
#include <aomap_pars_fragment>
#include <lightmap_pars_fragment>
#include <envmap_common_pars_fragment>
#include <envmap_pars_fragment>
#include <fog_pars_fragment>
#include <specularmap_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( diffuse, opacity );
	#include <clipping_planes_fragment>
	#include <logdepthbuf_fragment>
	#include <map_fragment>
	#include <color_fragment>
	#include <alphamap_fragment>
	#include <alphatest_fragment>
	#include <alphahash_fragment>
	#include <specularmap_fragment>
	ReflectedLight reflectedLight = ReflectedLight( vec3( 0.0 ), vec3( 0.0 ), vec3( 0.0 ), vec3( 0.0 ) );
	#ifdef USE_LIGHTMAP
		vec4 lightMapTexel = texture2D( lightMap, vLightMapUv );
		reflectedLight.indirectDiffuse += lightMapTexel.rgb * lightMapIntensity * RECIPROCAL_PI;
	#else
		reflectedLight.indirectDiffuse += vec3( 1.0 );
	#endif
	#include <aomap_fragment>
	reflectedLight.indirectDiffuse *= diffuseColor.rgb;
	vec3 outgoingLight = reflectedLight.indirectDiffuse;
	#include <envmap_fragment>
	#include <opaque_fragment>
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
	#include <fog_fragment>
	#include <premultiplied_alpha_fragment>
	#include <dithering_fragment>
}`,meshlambert_vert:`#define LAMBERT
varying vec3 vViewPosition;
#include <common>
#include <batching_pars_vertex>
#include <uv_pars_vertex>
#include <displacementmap_pars_vertex>
#include <envmap_pars_vertex>
#include <color_pars_vertex>
#include <fog_pars_vertex>
#include <normal_pars_vertex>
#include <morphtarget_pars_vertex>
#include <skinning_pars_vertex>
#include <shadowmap_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
void main() {
	#include <uv_vertex>
	#include <color_vertex>
	#include <morphinstance_vertex>
	#include <morphcolor_vertex>
	#include <batching_vertex>
	#include <beginnormal_vertex>
	#include <morphnormal_vertex>
	#include <skinbase_vertex>
	#include <skinnormal_vertex>
	#include <defaultnormal_vertex>
	#include <normal_vertex>
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <skinning_vertex>
	#include <displacementmap_vertex>
	#include <project_vertex>
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
	vViewPosition = - mvPosition.xyz;
	#include <worldpos_vertex>
	#include <envmap_vertex>
	#include <shadowmap_vertex>
	#include <fog_vertex>
}`,meshlambert_frag:`#define LAMBERT
uniform vec3 diffuse;
uniform vec3 emissive;
uniform float opacity;
#include <common>
#include <dithering_pars_fragment>
#include <color_pars_fragment>
#include <uv_pars_fragment>
#include <map_pars_fragment>
#include <alphamap_pars_fragment>
#include <alphatest_pars_fragment>
#include <alphahash_pars_fragment>
#include <aomap_pars_fragment>
#include <lightmap_pars_fragment>
#include <emissivemap_pars_fragment>
#include <cube_uv_reflection_fragment>
#include <envmap_common_pars_fragment>
#include <envmap_pars_fragment>
#include <envmap_physical_pars_fragment>
#include <fog_pars_fragment>
#include <bsdfs>
#include <lights_pars_begin>
#include <normal_pars_fragment>
#include <lights_lambert_pars_fragment>
#include <shadowmap_pars_fragment>
#include <bumpmap_pars_fragment>
#include <normalmap_pars_fragment>
#include <specularmap_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( diffuse, opacity );
	#include <clipping_planes_fragment>
	ReflectedLight reflectedLight = ReflectedLight( vec3( 0.0 ), vec3( 0.0 ), vec3( 0.0 ), vec3( 0.0 ) );
	vec3 totalEmissiveRadiance = emissive;
	#include <logdepthbuf_fragment>
	#include <map_fragment>
	#include <color_fragment>
	#include <alphamap_fragment>
	#include <alphatest_fragment>
	#include <alphahash_fragment>
	#include <specularmap_fragment>
	#include <normal_fragment_begin>
	#include <normal_fragment_maps>
	#include <emissivemap_fragment>
	#include <lights_lambert_fragment>
	#include <lights_fragment_begin>
	#include <lights_fragment_maps>
	#include <lights_fragment_end>
	#include <aomap_fragment>
	vec3 outgoingLight = reflectedLight.directDiffuse + reflectedLight.indirectDiffuse + totalEmissiveRadiance;
	#include <envmap_fragment>
	#include <opaque_fragment>
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
	#include <fog_fragment>
	#include <premultiplied_alpha_fragment>
	#include <dithering_fragment>
}`,meshmatcap_vert:`#define MATCAP
varying vec3 vViewPosition;
#include <common>
#include <batching_pars_vertex>
#include <uv_pars_vertex>
#include <color_pars_vertex>
#include <displacementmap_pars_vertex>
#include <fog_pars_vertex>
#include <normal_pars_vertex>
#include <morphtarget_pars_vertex>
#include <skinning_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
void main() {
	#include <uv_vertex>
	#include <color_vertex>
	#include <morphinstance_vertex>
	#include <morphcolor_vertex>
	#include <batching_vertex>
	#include <beginnormal_vertex>
	#include <morphnormal_vertex>
	#include <skinbase_vertex>
	#include <skinnormal_vertex>
	#include <defaultnormal_vertex>
	#include <normal_vertex>
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <skinning_vertex>
	#include <displacementmap_vertex>
	#include <project_vertex>
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
	#include <fog_vertex>
	vViewPosition = - mvPosition.xyz;
}`,meshmatcap_frag:`#define MATCAP
uniform vec3 diffuse;
uniform float opacity;
uniform sampler2D matcap;
varying vec3 vViewPosition;
#include <common>
#include <dithering_pars_fragment>
#include <color_pars_fragment>
#include <uv_pars_fragment>
#include <map_pars_fragment>
#include <alphamap_pars_fragment>
#include <alphatest_pars_fragment>
#include <alphahash_pars_fragment>
#include <fog_pars_fragment>
#include <normal_pars_fragment>
#include <bumpmap_pars_fragment>
#include <normalmap_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( diffuse, opacity );
	#include <clipping_planes_fragment>
	#include <logdepthbuf_fragment>
	#include <map_fragment>
	#include <color_fragment>
	#include <alphamap_fragment>
	#include <alphatest_fragment>
	#include <alphahash_fragment>
	#include <normal_fragment_begin>
	#include <normal_fragment_maps>
	vec3 viewDir = normalize( vViewPosition );
	vec3 x = normalize( vec3( viewDir.z, 0.0, - viewDir.x ) );
	vec3 y = cross( viewDir, x );
	vec2 uv = vec2( dot( x, normal ), dot( y, normal ) ) * 0.495 + 0.5;
	#ifdef USE_MATCAP
		vec4 matcapColor = texture2D( matcap, uv );
	#else
		vec4 matcapColor = vec4( vec3( mix( 0.2, 0.8, uv.y ) ), 1.0 );
	#endif
	vec3 outgoingLight = diffuseColor.rgb * matcapColor.rgb;
	#include <opaque_fragment>
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
	#include <fog_fragment>
	#include <premultiplied_alpha_fragment>
	#include <dithering_fragment>
}`,meshnormal_vert:`#define NORMAL
#if defined( FLAT_SHADED ) || defined( USE_BUMPMAP ) || defined( USE_NORMALMAP_TANGENTSPACE )
	varying vec3 vViewPosition;
#endif
#include <common>
#include <batching_pars_vertex>
#include <uv_pars_vertex>
#include <displacementmap_pars_vertex>
#include <normal_pars_vertex>
#include <morphtarget_pars_vertex>
#include <skinning_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
void main() {
	#include <uv_vertex>
	#include <batching_vertex>
	#include <beginnormal_vertex>
	#include <morphinstance_vertex>
	#include <morphnormal_vertex>
	#include <skinbase_vertex>
	#include <skinnormal_vertex>
	#include <defaultnormal_vertex>
	#include <normal_vertex>
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <skinning_vertex>
	#include <displacementmap_vertex>
	#include <project_vertex>
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
#if defined( FLAT_SHADED ) || defined( USE_BUMPMAP ) || defined( USE_NORMALMAP_TANGENTSPACE )
	vViewPosition = - mvPosition.xyz;
#endif
}`,meshnormal_frag:`#define NORMAL
uniform float opacity;
#if defined( FLAT_SHADED ) || defined( USE_BUMPMAP ) || defined( USE_NORMALMAP_TANGENTSPACE )
	varying vec3 vViewPosition;
#endif
#include <uv_pars_fragment>
#include <normal_pars_fragment>
#include <bumpmap_pars_fragment>
#include <normalmap_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( 0.0, 0.0, 0.0, opacity );
	#include <clipping_planes_fragment>
	#include <logdepthbuf_fragment>
	#include <normal_fragment_begin>
	#include <normal_fragment_maps>
	gl_FragColor = vec4( normalize( normal ) * 0.5 + 0.5, diffuseColor.a );
	#ifdef OPAQUE
		gl_FragColor.a = 1.0;
	#endif
}`,meshphong_vert:`#define PHONG
varying vec3 vViewPosition;
#include <common>
#include <batching_pars_vertex>
#include <uv_pars_vertex>
#include <displacementmap_pars_vertex>
#include <envmap_pars_vertex>
#include <color_pars_vertex>
#include <fog_pars_vertex>
#include <normal_pars_vertex>
#include <morphtarget_pars_vertex>
#include <skinning_pars_vertex>
#include <shadowmap_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
void main() {
	#include <uv_vertex>
	#include <color_vertex>
	#include <morphcolor_vertex>
	#include <batching_vertex>
	#include <beginnormal_vertex>
	#include <morphinstance_vertex>
	#include <morphnormal_vertex>
	#include <skinbase_vertex>
	#include <skinnormal_vertex>
	#include <defaultnormal_vertex>
	#include <normal_vertex>
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <skinning_vertex>
	#include <displacementmap_vertex>
	#include <project_vertex>
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
	vViewPosition = - mvPosition.xyz;
	#include <worldpos_vertex>
	#include <envmap_vertex>
	#include <shadowmap_vertex>
	#include <fog_vertex>
}`,meshphong_frag:`#define PHONG
uniform vec3 diffuse;
uniform vec3 emissive;
uniform vec3 specular;
uniform float shininess;
uniform float opacity;
#include <common>
#include <dithering_pars_fragment>
#include <color_pars_fragment>
#include <uv_pars_fragment>
#include <map_pars_fragment>
#include <alphamap_pars_fragment>
#include <alphatest_pars_fragment>
#include <alphahash_pars_fragment>
#include <aomap_pars_fragment>
#include <lightmap_pars_fragment>
#include <emissivemap_pars_fragment>
#include <cube_uv_reflection_fragment>
#include <envmap_common_pars_fragment>
#include <envmap_pars_fragment>
#include <envmap_physical_pars_fragment>
#include <fog_pars_fragment>
#include <bsdfs>
#include <lights_pars_begin>
#include <normal_pars_fragment>
#include <lights_phong_pars_fragment>
#include <shadowmap_pars_fragment>
#include <bumpmap_pars_fragment>
#include <normalmap_pars_fragment>
#include <specularmap_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( diffuse, opacity );
	#include <clipping_planes_fragment>
	ReflectedLight reflectedLight = ReflectedLight( vec3( 0.0 ), vec3( 0.0 ), vec3( 0.0 ), vec3( 0.0 ) );
	vec3 totalEmissiveRadiance = emissive;
	#include <logdepthbuf_fragment>
	#include <map_fragment>
	#include <color_fragment>
	#include <alphamap_fragment>
	#include <alphatest_fragment>
	#include <alphahash_fragment>
	#include <specularmap_fragment>
	#include <normal_fragment_begin>
	#include <normal_fragment_maps>
	#include <emissivemap_fragment>
	#include <lights_phong_fragment>
	#include <lights_fragment_begin>
	#include <lights_fragment_maps>
	#include <lights_fragment_end>
	#include <aomap_fragment>
	vec3 outgoingLight = reflectedLight.directDiffuse + reflectedLight.indirectDiffuse + reflectedLight.directSpecular + reflectedLight.indirectSpecular + totalEmissiveRadiance;
	#include <envmap_fragment>
	#include <opaque_fragment>
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
	#include <fog_fragment>
	#include <premultiplied_alpha_fragment>
	#include <dithering_fragment>
}`,meshphysical_vert:`#define STANDARD
varying vec3 vViewPosition;
#ifdef USE_TRANSMISSION
	varying vec3 vWorldPosition;
#endif
#include <common>
#include <batching_pars_vertex>
#include <uv_pars_vertex>
#include <displacementmap_pars_vertex>
#include <color_pars_vertex>
#include <fog_pars_vertex>
#include <normal_pars_vertex>
#include <morphtarget_pars_vertex>
#include <skinning_pars_vertex>
#include <shadowmap_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
void main() {
	#include <uv_vertex>
	#include <color_vertex>
	#include <morphinstance_vertex>
	#include <morphcolor_vertex>
	#include <batching_vertex>
	#include <beginnormal_vertex>
	#include <morphnormal_vertex>
	#include <skinbase_vertex>
	#include <skinnormal_vertex>
	#include <defaultnormal_vertex>
	#include <normal_vertex>
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <skinning_vertex>
	#include <displacementmap_vertex>
	#include <project_vertex>
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
	vViewPosition = - mvPosition.xyz;
	#include <worldpos_vertex>
	#include <shadowmap_vertex>
	#include <fog_vertex>
#ifdef USE_TRANSMISSION
	vWorldPosition = worldPosition.xyz;
#endif
}`,meshphysical_frag:`#define STANDARD
#ifdef PHYSICAL
	#define IOR
	#define USE_SPECULAR
#endif
uniform vec3 diffuse;
uniform vec3 emissive;
uniform float roughness;
uniform float metalness;
uniform float opacity;
#ifdef IOR
	uniform float ior;
#endif
#ifdef USE_SPECULAR
	uniform float specularIntensity;
	uniform vec3 specularColor;
	#ifdef USE_SPECULAR_COLORMAP
		uniform sampler2D specularColorMap;
	#endif
	#ifdef USE_SPECULAR_INTENSITYMAP
		uniform sampler2D specularIntensityMap;
	#endif
#endif
#ifdef USE_CLEARCOAT
	uniform float clearcoat;
	uniform float clearcoatRoughness;
#endif
#ifdef USE_DISPERSION
	uniform float dispersion;
#endif
#ifdef USE_RETROREFLECTION
	uniform float retroreflectivity;
#endif
#ifdef USE_IRIDESCENCE
	uniform float iridescence;
	uniform float iridescenceIOR;
	uniform float iridescenceThicknessMinimum;
	uniform float iridescenceThicknessMaximum;
#endif
#ifdef USE_SHEEN
	uniform vec3 sheenColor;
	uniform float sheenRoughness;
	#ifdef USE_SHEEN_COLORMAP
		uniform sampler2D sheenColorMap;
	#endif
	#ifdef USE_SHEEN_ROUGHNESSMAP
		uniform sampler2D sheenRoughnessMap;
	#endif
#endif
#ifdef USE_ANISOTROPY
	uniform vec2 anisotropyVector;
	#ifdef USE_ANISOTROPYMAP
		uniform sampler2D anisotropyMap;
	#endif
#endif
varying vec3 vViewPosition;
#include <common>
#include <dithering_pars_fragment>
#include <color_pars_fragment>
#include <uv_pars_fragment>
#include <map_pars_fragment>
#include <alphamap_pars_fragment>
#include <alphatest_pars_fragment>
#include <alphahash_pars_fragment>
#include <aomap_pars_fragment>
#include <lightmap_pars_fragment>
#include <emissivemap_pars_fragment>
#include <iridescence_fragment>
#include <cube_uv_reflection_fragment>
#include <envmap_common_pars_fragment>
#include <envmap_physical_pars_fragment>
#include <fog_pars_fragment>
#include <lights_pars_begin>
#include <normal_pars_fragment>
#include <lights_physical_pars_fragment>
#include <transmission_pars_fragment>
#include <shadowmap_pars_fragment>
#include <bumpmap_pars_fragment>
#include <normalmap_pars_fragment>
#include <clearcoat_pars_fragment>
#include <iridescence_pars_fragment>
#include <roughnessmap_pars_fragment>
#include <metalnessmap_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( diffuse, opacity );
	#include <clipping_planes_fragment>
	ReflectedLight reflectedLight = ReflectedLight( vec3( 0.0 ), vec3( 0.0 ), vec3( 0.0 ), vec3( 0.0 ) );
	vec3 totalEmissiveRadiance = emissive;
	#include <logdepthbuf_fragment>
	#include <map_fragment>
	#include <color_fragment>
	#include <alphamap_fragment>
	#include <alphatest_fragment>
	#include <alphahash_fragment>
	#include <roughnessmap_fragment>
	#include <metalnessmap_fragment>
	#include <normal_fragment_begin>
	#include <normal_fragment_maps>
	#include <clearcoat_normal_fragment_begin>
	#include <clearcoat_normal_fragment_maps>
	#include <emissivemap_fragment>
	#include <lights_physical_fragment>
	#include <lights_fragment_begin>
	#include <lights_fragment_maps>
	#include <lights_fragment_end>
	#include <aomap_fragment>
	vec3 totalDiffuse = reflectedLight.directDiffuse + reflectedLight.indirectDiffuse;
	vec3 totalSpecular = reflectedLight.directSpecular + reflectedLight.indirectSpecular;
	#include <transmission_fragment>
	vec3 outgoingLight = totalDiffuse + totalSpecular + totalEmissiveRadiance;
	#ifdef USE_SHEEN
 
		outgoingLight = outgoingLight + sheenSpecularDirect + sheenSpecularIndirect;
 
 	#endif
	#ifdef USE_CLEARCOAT
		float dotNVcc = saturate( dot( geometryClearcoatNormal, geometryViewDir ) );
		vec3 Fcc = F_Schlick( material.clearcoatF0, material.clearcoatF90, dotNVcc );
		outgoingLight = outgoingLight * ( 1.0 - material.clearcoat * Fcc ) + ( clearcoatSpecularDirect + clearcoatSpecularIndirect ) * material.clearcoat;
	#endif
	#include <opaque_fragment>
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
	#include <fog_fragment>
	#include <premultiplied_alpha_fragment>
	#include <dithering_fragment>
}`,meshtoon_vert:`#define TOON
varying vec3 vViewPosition;
#include <common>
#include <batching_pars_vertex>
#include <uv_pars_vertex>
#include <displacementmap_pars_vertex>
#include <color_pars_vertex>
#include <fog_pars_vertex>
#include <normal_pars_vertex>
#include <morphtarget_pars_vertex>
#include <skinning_pars_vertex>
#include <shadowmap_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
void main() {
	#include <uv_vertex>
	#include <color_vertex>
	#include <morphinstance_vertex>
	#include <morphcolor_vertex>
	#include <batching_vertex>
	#include <beginnormal_vertex>
	#include <morphnormal_vertex>
	#include <skinbase_vertex>
	#include <skinnormal_vertex>
	#include <defaultnormal_vertex>
	#include <normal_vertex>
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <skinning_vertex>
	#include <displacementmap_vertex>
	#include <project_vertex>
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
	vViewPosition = - mvPosition.xyz;
	#include <worldpos_vertex>
	#include <shadowmap_vertex>
	#include <fog_vertex>
}`,meshtoon_frag:`#define TOON
uniform vec3 diffuse;
uniform vec3 emissive;
uniform float opacity;
#include <common>
#include <dithering_pars_fragment>
#include <color_pars_fragment>
#include <uv_pars_fragment>
#include <map_pars_fragment>
#include <alphamap_pars_fragment>
#include <alphatest_pars_fragment>
#include <alphahash_pars_fragment>
#include <aomap_pars_fragment>
#include <lightmap_pars_fragment>
#include <emissivemap_pars_fragment>
#include <gradientmap_pars_fragment>
#include <fog_pars_fragment>
#include <bsdfs>
#include <lights_pars_begin>
#include <normal_pars_fragment>
#include <lights_toon_pars_fragment>
#include <shadowmap_pars_fragment>
#include <bumpmap_pars_fragment>
#include <normalmap_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( diffuse, opacity );
	#include <clipping_planes_fragment>
	ReflectedLight reflectedLight = ReflectedLight( vec3( 0.0 ), vec3( 0.0 ), vec3( 0.0 ), vec3( 0.0 ) );
	vec3 totalEmissiveRadiance = emissive;
	#include <logdepthbuf_fragment>
	#include <map_fragment>
	#include <color_fragment>
	#include <alphamap_fragment>
	#include <alphatest_fragment>
	#include <alphahash_fragment>
	#include <normal_fragment_begin>
	#include <normal_fragment_maps>
	#include <emissivemap_fragment>
	#include <lights_toon_fragment>
	#include <lights_fragment_begin>
	#include <lights_fragment_maps>
	#include <lights_fragment_end>
	#include <aomap_fragment>
	vec3 outgoingLight = reflectedLight.directDiffuse + reflectedLight.indirectDiffuse + totalEmissiveRadiance;
	#include <opaque_fragment>
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
	#include <fog_fragment>
	#include <premultiplied_alpha_fragment>
	#include <dithering_fragment>
}`,points_vert:`uniform float size;
uniform float scale;
#include <common>
#include <color_pars_vertex>
#include <fog_pars_vertex>
#include <morphtarget_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
#ifdef USE_POINTS_UV
	varying vec2 vUv;
	uniform mat3 uvTransform;
#endif
void main() {
	#ifdef USE_POINTS_UV
		vUv = ( uvTransform * vec3( uv, 1 ) ).xy;
	#endif
	#include <color_vertex>
	#include <morphinstance_vertex>
	#include <morphcolor_vertex>
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <project_vertex>
	gl_PointSize = size;
	#ifdef USE_SIZEATTENUATION
		bool isPerspective = isPerspectiveMatrix( projectionMatrix );
		if ( isPerspective ) gl_PointSize *= ( scale / - mvPosition.z );
	#endif
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
	#include <worldpos_vertex>
	#include <fog_vertex>
}`,points_frag:`uniform vec3 diffuse;
uniform float opacity;
#include <common>
#include <color_pars_fragment>
#include <map_particle_pars_fragment>
#include <alphatest_pars_fragment>
#include <alphahash_pars_fragment>
#include <fog_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( diffuse, opacity );
	#include <clipping_planes_fragment>
	vec3 outgoingLight = vec3( 0.0 );
	#include <logdepthbuf_fragment>
	#include <map_particle_fragment>
	#include <color_fragment>
	#include <alphatest_fragment>
	#include <alphahash_fragment>
	outgoingLight = diffuseColor.rgb;
	#include <opaque_fragment>
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
	#include <fog_fragment>
	#include <premultiplied_alpha_fragment>
}`,shadow_vert:`#include <common>
#include <batching_pars_vertex>
#include <fog_pars_vertex>
#include <morphtarget_pars_vertex>
#include <skinning_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <shadowmap_pars_vertex>
void main() {
	#include <batching_vertex>
	#include <beginnormal_vertex>
	#include <morphinstance_vertex>
	#include <morphnormal_vertex>
	#include <skinbase_vertex>
	#include <skinnormal_vertex>
	#include <defaultnormal_vertex>
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <skinning_vertex>
	#include <project_vertex>
	#include <logdepthbuf_vertex>
	#include <worldpos_vertex>
	#include <shadowmap_vertex>
	#include <fog_vertex>
}`,shadow_frag:`uniform vec3 color;
uniform float opacity;
#include <common>
#include <fog_pars_fragment>
#include <bsdfs>
#include <lights_pars_begin>
#include <logdepthbuf_pars_fragment>
#include <shadowmap_pars_fragment>
#include <shadowmask_pars_fragment>
void main() {
	#include <logdepthbuf_fragment>
	gl_FragColor = vec4( color, opacity * ( 1.0 - getShadowMask() ) );
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
	#include <fog_fragment>
	#include <premultiplied_alpha_fragment>
}`,sprite_vert:`uniform float rotation;
uniform vec2 center;
#include <common>
#include <uv_pars_vertex>
#include <fog_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
void main() {
	#include <uv_vertex>
	vec4 mvPosition = modelViewMatrix[ 3 ];
	vec2 scale = vec2( length( modelMatrix[ 0 ].xyz ), length( modelMatrix[ 1 ].xyz ) );
	#ifndef USE_SIZEATTENUATION
		bool isPerspective = isPerspectiveMatrix( projectionMatrix );
		if ( isPerspective ) scale *= - mvPosition.z;
	#endif
	vec2 alignedPosition = ( position.xy - ( center - vec2( 0.5 ) ) ) * scale;
	vec2 rotatedPosition;
	rotatedPosition.x = cos( rotation ) * alignedPosition.x - sin( rotation ) * alignedPosition.y;
	rotatedPosition.y = sin( rotation ) * alignedPosition.x + cos( rotation ) * alignedPosition.y;
	mvPosition.xy += rotatedPosition;
	gl_Position = projectionMatrix * mvPosition;
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
	#include <fog_vertex>
}`,sprite_frag:`uniform vec3 diffuse;
uniform float opacity;
#include <common>
#include <uv_pars_fragment>
#include <map_pars_fragment>
#include <alphamap_pars_fragment>
#include <alphatest_pars_fragment>
#include <alphahash_pars_fragment>
#include <fog_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( diffuse, opacity );
	#include <clipping_planes_fragment>
	vec3 outgoingLight = vec3( 0.0 );
	#include <logdepthbuf_fragment>
	#include <map_fragment>
	#include <alphamap_fragment>
	#include <alphatest_fragment>
	#include <alphahash_fragment>
	outgoingLight = diffuseColor.rgb;
	#include <opaque_fragment>
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
	#include <fog_fragment>
}`},Q={common:{diffuse:{value:new Y(16777215)},opacity:{value:1},map:{value:null},mapTransform:{value:new K},alphaMap:{value:null},alphaMapTransform:{value:new K},alphaTest:{value:0}},specularmap:{specularMap:{value:null},specularMapTransform:{value:new K}},envmap:{envMap:{value:null},envMapRotation:{value:new K},reflectivity:{value:1},ior:{value:1.5},refractionRatio:{value:.98},dfgLUT:{value:null}},aomap:{aoMap:{value:null},aoMapIntensity:{value:1},aoMapTransform:{value:new K}},lightmap:{lightMap:{value:null},lightMapIntensity:{value:1},lightMapTransform:{value:new K}},bumpmap:{bumpMap:{value:null},bumpMapTransform:{value:new K},bumpScale:{value:1}},normalmap:{normalMap:{value:null},normalMapTransform:{value:new K},normalScale:{value:new W(1,1)}},displacementmap:{displacementMap:{value:null},displacementMapTransform:{value:new K},displacementScale:{value:1},displacementBias:{value:0}},emissivemap:{emissiveMap:{value:null},emissiveMapTransform:{value:new K}},metalnessmap:{metalnessMap:{value:null},metalnessMapTransform:{value:new K}},roughnessmap:{roughnessMap:{value:null},roughnessMapTransform:{value:new K}},gradientmap:{gradientMap:{value:null}},fog:{fogDensity:{value:25e-5},fogNear:{value:1},fogFar:{value:2e3},fogColor:{value:new Y(16777215)}},lights:{ambientLightColor:{value:[]},lightProbe:{value:[]},sunLights:{value:[],properties:{direction:{},color:{}}},sunLightShadows:{value:[],properties:{shadowIntensity:1,shadowBias:{},shadowNormalBias:{},shadowRadius:{},shadowMapSize:{}}},sunShadowMatrix:{value:[]},sunShadowCascade:{value:[]},directionalLights:{value:[],properties:{direction:{},color:{}}},directionalLightShadows:{value:[],properties:{shadowIntensity:1,shadowBias:{},shadowNormalBias:{},shadowRadius:{},shadowMapSize:{}}},directionalShadowMatrix:{value:[]},spotLights:{value:[],properties:{color:{},position:{},direction:{},distance:{},coneCos:{},penumbraCos:{},decay:{}}},spotLightShadows:{value:[],properties:{shadowIntensity:1,shadowBias:{},shadowNormalBias:{},shadowRadius:{},shadowMapSize:{}}},spotLightMap:{value:[]},spotLightMatrix:{value:[]},pointLights:{value:[],properties:{color:{},position:{},decay:{},distance:{}}},pointLightShadows:{value:[],properties:{shadowIntensity:1,shadowBias:{},shadowNormalBias:{},shadowRadius:{},shadowMapSize:{},shadowCameraNear:{},shadowCameraFar:{}}},pointShadowMatrix:{value:[]},hemisphereLights:{value:[],properties:{direction:{},skyColor:{},groundColor:{}}},rectAreaLights:{value:[],properties:{color:{},position:{},width:{},height:{}}},ltc_1:{value:null},ltc_2:{value:null},probesSH:{value:null},probesMin:{value:new G},probesMax:{value:new G},probesResolution:{value:new G}},points:{diffuse:{value:new Y(16777215)},opacity:{value:1},size:{value:1},scale:{value:1},map:{value:null},alphaMap:{value:null},alphaMapTransform:{value:new K},alphaTest:{value:0},uvTransform:{value:new K}},sprite:{diffuse:{value:new Y(16777215)},opacity:{value:1},center:{value:new W(.5,.5)},rotation:{value:0},map:{value:null},mapTransform:{value:new K},alphaMap:{value:null},alphaMapTransform:{value:new K},alphaTest:{value:0}}},ho={basic:{uniforms:aa([Q.common,Q.specularmap,Q.envmap,Q.aomap,Q.lightmap,Q.fog]),vertexShader:Z.meshbasic_vert,fragmentShader:Z.meshbasic_frag},lambert:{uniforms:aa([Q.common,Q.specularmap,Q.envmap,Q.aomap,Q.lightmap,Q.emissivemap,Q.bumpmap,Q.normalmap,Q.displacementmap,Q.fog,Q.lights,{emissive:{value:new Y(0)},envMapIntensity:{value:1}}]),vertexShader:Z.meshlambert_vert,fragmentShader:Z.meshlambert_frag},phong:{uniforms:aa([Q.common,Q.specularmap,Q.envmap,Q.aomap,Q.lightmap,Q.emissivemap,Q.bumpmap,Q.normalmap,Q.displacementmap,Q.fog,Q.lights,{emissive:{value:new Y(0)},specular:{value:new Y(1118481)},shininess:{value:30},envMapIntensity:{value:1}}]),vertexShader:Z.meshphong_vert,fragmentShader:Z.meshphong_frag},standard:{uniforms:aa([Q.common,Q.envmap,Q.aomap,Q.lightmap,Q.emissivemap,Q.bumpmap,Q.normalmap,Q.displacementmap,Q.roughnessmap,Q.metalnessmap,Q.fog,Q.lights,{emissive:{value:new Y(0)},roughness:{value:1},metalness:{value:0},envMapIntensity:{value:1}}]),vertexShader:Z.meshphysical_vert,fragmentShader:Z.meshphysical_frag},toon:{uniforms:aa([Q.common,Q.aomap,Q.lightmap,Q.emissivemap,Q.bumpmap,Q.normalmap,Q.displacementmap,Q.gradientmap,Q.fog,Q.lights,{emissive:{value:new Y(0)}}]),vertexShader:Z.meshtoon_vert,fragmentShader:Z.meshtoon_frag},matcap:{uniforms:aa([Q.common,Q.bumpmap,Q.normalmap,Q.displacementmap,Q.fog,{matcap:{value:null}}]),vertexShader:Z.meshmatcap_vert,fragmentShader:Z.meshmatcap_frag},points:{uniforms:aa([Q.points,Q.fog]),vertexShader:Z.points_vert,fragmentShader:Z.points_frag},dashed:{uniforms:aa([Q.common,Q.fog,{scale:{value:1},dashSize:{value:1},totalSize:{value:2}}]),vertexShader:Z.linedashed_vert,fragmentShader:Z.linedashed_frag},depth:{uniforms:aa([Q.common,Q.displacementmap]),vertexShader:Z.depth_vert,fragmentShader:Z.depth_frag},normal:{uniforms:aa([Q.common,Q.bumpmap,Q.normalmap,Q.displacementmap,{opacity:{value:1}}]),vertexShader:Z.meshnormal_vert,fragmentShader:Z.meshnormal_frag},sprite:{uniforms:aa([Q.sprite,Q.fog]),vertexShader:Z.sprite_vert,fragmentShader:Z.sprite_frag},background:{uniforms:{uvTransform:{value:new K},t2D:{value:null},backgroundIntensity:{value:1}},vertexShader:Z.background_vert,fragmentShader:Z.background_frag},backgroundCube:{uniforms:{envMap:{value:null},backgroundBlurriness:{value:0},backgroundIntensity:{value:1},backgroundRotation:{value:new K}},vertexShader:Z.backgroundCube_vert,fragmentShader:Z.backgroundCube_frag},cube:{uniforms:{tCube:{value:null},tFlip:{value:-1},opacity:{value:1}},vertexShader:Z.cube_vert,fragmentShader:Z.cube_frag},equirect:{uniforms:{tEquirect:{value:null}},vertexShader:Z.equirect_vert,fragmentShader:Z.equirect_frag},distance:{uniforms:aa([Q.common,Q.displacementmap,{referencePosition:{value:new G},nearDistance:{value:1},farDistance:{value:1e3}}]),vertexShader:Z.distance_vert,fragmentShader:Z.distance_frag},shadow:{uniforms:aa([Q.lights,Q.fog,{color:{value:new Y(0)},opacity:{value:1}}]),vertexShader:Z.shadow_vert,fragmentShader:Z.shadow_frag}};ho.physical={uniforms:aa([ho.standard.uniforms,{clearcoat:{value:0},clearcoatMap:{value:null},clearcoatMapTransform:{value:new K},clearcoatNormalMap:{value:null},clearcoatNormalMapTransform:{value:new K},clearcoatNormalScale:{value:new W(1,1)},clearcoatRoughness:{value:0},clearcoatRoughnessMap:{value:null},clearcoatRoughnessMapTransform:{value:new K},dispersion:{value:0},retroreflectivity:{value:0},iridescence:{value:0},iridescenceMap:{value:null},iridescenceMapTransform:{value:new K},iridescenceIOR:{value:1.3},iridescenceThicknessMinimum:{value:100},iridescenceThicknessMaximum:{value:400},iridescenceThicknessMap:{value:null},iridescenceThicknessMapTransform:{value:new K},sheen:{value:0},sheenColor:{value:new Y(0)},sheenColorMap:{value:null},sheenColorMapTransform:{value:new K},sheenRoughness:{value:1},sheenRoughnessMap:{value:null},sheenRoughnessMapTransform:{value:new K},transmission:{value:0},transmissionMap:{value:null},transmissionMapTransform:{value:new K},transmissionSamplerSize:{value:new W},transmissionSamplerMap:{value:null},thickness:{value:0},thicknessMap:{value:null},thicknessMapTransform:{value:new K},attenuationDistance:{value:0},attenuationColor:{value:new Y(0)},specularColor:{value:new Y(1,1,1)},specularColorMap:{value:null},specularColorMapTransform:{value:new K},specularIntensity:{value:1},specularIntensityMap:{value:null},specularIntensityMapTransform:{value:new K},anisotropyVector:{value:new W},anisotropyMap:{value:null},anisotropyMapTransform:{value:new K}}]),vertexShader:Z.meshphysical_vert,fragmentShader:Z.meshphysical_frag};var go={r:0,b:0,g:0},_o=new J,vo=new K;vo.set(-1,0,0,0,1,0,0,0,1);function yo(e,t,n,r,i,a){let o=new Y(0),s=i===!0?0:1,c,l,u=null,d=0,f=null;function p(e){let n=e.isScene===!0?e.background:null;if(n&&n.isTexture){let r=e.backgroundBlurriness>0;n=t.get(n,r)}return n}function m(t){let r=!1,i=p(t);i===null?g(o,s):i&&i.isColor&&(g(i,1),r=!0);let c=e.xr.getEnvironmentBlendMode();c===`additive`?n.buffers.color.setClear(0,0,0,1,a):c===`alpha-blend`&&n.buffers.color.setClear(0,0,0,0,a),(e.autoClear||r)&&(n.buffers.depth.setTest(!0),n.buffers.depth.setMask(!0),n.buffers.color.setMask(!0),e.clear(e.autoClearColor,e.autoClearDepth,e.autoClearStencil))}function h(t,n){let i=p(n);i&&(i.isCubeTexture||i.mapping===306)?(l===void 0&&(l=new ci(new Xi(1,1,1),new fa({name:`BackgroundCubeMaterial`,uniforms:ia(ho.backgroundCube.uniforms),vertexShader:ho.backgroundCube.vertexShader,fragmentShader:ho.backgroundCube.fragmentShader,side:1,depthTest:!1,depthWrite:!1,fog:!1,allowOverride:!1})),l.geometry.deleteAttribute(`normal`),l.geometry.deleteAttribute(`uv`),l.onBeforeRender=function(e,t,n){this.matrixWorld.copyPosition(n.matrixWorld)},Object.defineProperty(l.material,"envMap",{get:function(){return this.uniforms.envMap.value}}),r.update(l)),l.material.uniforms.envMap.value=i,l.material.uniforms.backgroundBlurriness.value=n.backgroundBlurriness,l.material.uniforms.backgroundIntensity.value=n.backgroundIntensity,l.material.uniforms.backgroundRotation.value.setFromMatrix4(_o.makeRotationFromEuler(n.backgroundRotation)).transpose(),i.isCubeTexture&&i.isRenderTargetTexture===!1&&l.material.uniforms.backgroundRotation.value.premultiply(vo),l.material.toneMapped=q.getTransfer(i.colorSpace)!==$e,(u!==i||d!==i.version||f!==e.toneMapping)&&(l.material.needsUpdate=!0,u=i,d=i.version,f=e.toneMapping),l.layers.enableAll(),t.unshift(l,l.geometry,l.material,0,0,null)):i&&i.isTexture&&(c===void 0&&(c=new ci(new na(2,2),new fa({name:`BackgroundMaterial`,uniforms:ia(ho.background.uniforms),vertexShader:ho.background.vertexShader,fragmentShader:ho.background.fragmentShader,side:0,depthTest:!1,depthWrite:!1,fog:!1,allowOverride:!1})),c.geometry.deleteAttribute(`normal`),Object.defineProperty(c.material,"map",{get:function(){return this.uniforms.t2D.value}}),r.update(c)),c.material.uniforms.t2D.value=i,c.material.uniforms.backgroundIntensity.value=n.backgroundIntensity,c.material.toneMapped=q.getTransfer(i.colorSpace)!==$e,i.matrixAutoUpdate===!0&&i.updateMatrix(),c.material.uniforms.uvTransform.value.copy(i.matrix),(u!==i||d!==i.version||f!==e.toneMapping)&&(c.material.needsUpdate=!0,u=i,d=i.version,f=e.toneMapping),c.layers.enableAll(),t.unshift(c,c.geometry,c.material,0,0,null))}function g(t,r){t.getRGB(go,ca(e)),n.buffers.color.setClear(go.r,go.g,go.b,r,a)}function _(){l!==void 0&&(l.geometry.dispose(),l.material.dispose(),l=void 0),c!==void 0&&(c.geometry.dispose(),c.material.dispose(),c=void 0)}return{getClearColor:function(){return o},setClearColor:function(e,t=1){o.set(e),s=t,g(o,s)},getClearAlpha:function(){return s},setClearAlpha:function(e){s=e,g(o,s)},render:m,addToRenderList:h,dispose:_}}function bo(e,t){let n=e.getParameter(e.MAX_VERTEX_ATTRIBS),r={},i=f(null),a=i,o=!1;function s(n,r,i,s,c){let u=!1,f=d(n,s,i,r);a!==f&&(a=f,l(a.object)),u=p(n,s,i,c),u&&m(n,s,i,c),c!==null&&t.update(c,e.ELEMENT_ARRAY_BUFFER),(u||o)&&(o=!1,b(n,r,i,s),c!==null&&e.bindBuffer(e.ELEMENT_ARRAY_BUFFER,t.get(c).buffer))}function c(){return e.createVertexArray()}function l(t){return e.bindVertexArray(t)}function u(t){return e.deleteVertexArray(t)}function d(e,t,n,i){let a=i.wireframe===!0,o=r[t.id];o===void 0&&(o={},r[t.id]=o);let s=e.isInstancedMesh===!0?e.id:0,l=o[s];l===void 0&&(l={},o[s]=l);let u=l[n.id];u===void 0&&(u={},l[n.id]=u);let d=u[a];return d===void 0&&(d=f(c()),u[a]=d),d}function f(e){let t=[],r=[],i=[];for(let e=0;e<n;e++)t[e]=0,r[e]=0,i[e]=0;return{geometry:null,program:null,wireframe:!1,newAttributes:t,enabledAttributes:r,attributeDivisors:i,object:e,attributes:{},index:null}}function p(e,t,n,r){let i=a.attributes,o=t.attributes,s=0,c=n.getAttributes();for(let t in c)if(c[t].location>=0){let n=i[t],r=o[t];if(r===void 0&&(t===`instanceMatrix`&&e.instanceMatrix&&(r=e.instanceMatrix),t===`instanceColor`&&e.instanceColor&&(r=e.instanceColor)),n===void 0||n.attribute!==r||r&&n.data!==r.data)return!0;s++}return a.attributesNum!==s||a.index!==r}function m(e,t,n,r){let i={},o=t.attributes,s=0,c=n.getAttributes();for(let t in c)if(c[t].location>=0){let n=o[t];n===void 0&&(t===`instanceMatrix`&&e.instanceMatrix&&(n=e.instanceMatrix),t===`instanceColor`&&e.instanceColor&&(n=e.instanceColor));let r={};r.attribute=n,n&&n.data&&(r.data=n.data),i[t]=r,s++}a.attributes=i,a.attributesNum=s,a.index=r}function h(){let e=a.newAttributes;for(let t=0,n=e.length;t<n;t++)e[t]=0}function g(e){_(e,0)}function _(t,n){let r=a.newAttributes,i=a.enabledAttributes,o=a.attributeDivisors;r[t]=1,i[t]===0&&(e.enableVertexAttribArray(t),i[t]=1),o[t]!==n&&(e.vertexAttribDivisor(t,n),o[t]=n)}function v(){let t=a.newAttributes,n=a.enabledAttributes;for(let r=0,i=n.length;r<i;r++)n[r]!==t[r]&&(e.disableVertexAttribArray(r),n[r]=0)}function y(t,n,r,i,a,o,s){s===!0?e.vertexAttribIPointer(t,n,r,a,o):e.vertexAttribPointer(t,n,r,i,a,o)}function b(n,r,i,a){h();let o=a.attributes,s=i.getAttributes(),c=r.defaultAttributeValues;for(let r in s){let i=s[r];if(i.location>=0){let s=o[r];if(s===void 0&&(r===`instanceMatrix`&&n.instanceMatrix&&(s=n.instanceMatrix),r===`instanceColor`&&n.instanceColor&&(s=n.instanceColor)),s!==void 0){let r=s.normalized,o=s.itemSize,c=t.get(s);if(c===void 0)continue;let l=c.buffer,u=c.type,d=c.bytesPerElement,f=u===e.INT||u===e.UNSIGNED_INT||s.gpuType===1013;if(s.isInterleavedBufferAttribute){let t=s.data,c=t.stride,p=s.offset;if(t.isInstancedInterleavedBuffer){for(let e=0;e<i.locationSize;e++)_(i.location+e,t.meshPerAttribute);n.isInstancedMesh!==!0&&a._maxInstanceCount===void 0&&(a._maxInstanceCount=t.meshPerAttribute*t.count)}else for(let e=0;e<i.locationSize;e++)g(i.location+e);e.bindBuffer(e.ARRAY_BUFFER,l);for(let e=0;e<i.locationSize;e++)y(i.location+e,o/i.locationSize,u,r,c*d,(p+o/i.locationSize*e)*d,f)}else{if(s.isInstancedBufferAttribute){for(let e=0;e<i.locationSize;e++)_(i.location+e,s.meshPerAttribute);n.isInstancedMesh!==!0&&a._maxInstanceCount===void 0&&(a._maxInstanceCount=s.meshPerAttribute*s.count)}else for(let e=0;e<i.locationSize;e++)g(i.location+e);e.bindBuffer(e.ARRAY_BUFFER,l);for(let e=0;e<i.locationSize;e++)y(i.location+e,o/i.locationSize,u,r,o*d,o/i.locationSize*e*d,f)}}else if(c!==void 0){let t=c[r];if(t!==void 0)switch(t.length){case 2:e.vertexAttrib2fv(i.location,t);break;case 3:e.vertexAttrib3fv(i.location,t);break;case 4:e.vertexAttrib4fv(i.location,t);break;default:e.vertexAttrib1fv(i.location,t)}}}}v()}function x(){T();for(let e in r){let t=r[e];for(let e in t){let n=t[e];for(let e in n){let t=n[e];for(let e in t)u(t[e].object),delete t[e];delete n[e]}}delete r[e]}}function S(e){if(r[e.id]===void 0)return;let t=r[e.id];for(let e in t){let n=t[e];for(let e in n){let t=n[e];for(let e in t)u(t[e].object),delete t[e];delete n[e]}}delete r[e.id]}function C(e){for(let t in r){let n=r[t];for(let t in n){let r=n[t];if(r[e.id]===void 0)continue;let i=r[e.id];for(let e in i)u(i[e].object),delete i[e];delete r[e.id]}}}function w(e){for(let t in r){let n=r[t],i=e.isInstancedMesh===!0?e.id:0,a=n[i];if(a!==void 0){for(let e in a){let t=a[e];for(let e in t)u(t[e].object),delete t[e];delete a[e]}delete n[i],Object.keys(n).length===0&&delete r[t]}}}function T(){E(),o=!0,a!==i&&(a=i,l(a.object))}function E(){i.geometry=null,i.program=null,i.wireframe=!1}return{setup:s,reset:T,resetDefaultState:E,dispose:x,releaseStatesOfGeometry:S,releaseStatesOfObject:w,releaseStatesOfProgram:C,initAttributes:h,enableAttribute:g,disableUnusedAttributes:v}}function xo(e,t,n){let r;function i(e){r=e}function a(t,i){e.drawArrays(r,t,i),n.update(i,r,1)}function o(t,i,a){a!==0&&(e.drawArraysInstanced(r,t,i,a),n.update(i,r,a))}function s(e,i,a){if(a===0)return;t.get(`WEBGL_multi_draw`).multiDrawArraysWEBGL(r,e,0,i,0,a);let o=0;for(let e=0;e<a;e++)o+=i[e];n.update(o,r,1)}this.setMode=i,this.render=a,this.renderInstances=o,this.renderMultiDraw=s}function So(e,t,n,r){let i;function a(){if(i!==void 0)return i;if(t.has(`EXT_texture_filter_anisotropic`)===!0){let n=t.get(`EXT_texture_filter_anisotropic`);i=e.getParameter(n.MAX_TEXTURE_MAX_ANISOTROPY_EXT)}else i=0;return i}function o(t){return t===1023||r.convert(t)===e.getParameter(e.IMPLEMENTATION_COLOR_READ_FORMAT)}function s(n){let i=n===1016&&(t.has(`EXT_color_buffer_half_float`)||t.has(`EXT_color_buffer_float`));return!(n!==1009&&n!==1015&&!i&&r.convert(n)!==e.getParameter(e.IMPLEMENTATION_COLOR_READ_TYPE))}function c(t){if(t===`highp`){if(e.getShaderPrecisionFormat(e.VERTEX_SHADER,e.HIGH_FLOAT).precision>0&&e.getShaderPrecisionFormat(e.FRAGMENT_SHADER,e.HIGH_FLOAT).precision>0)return`highp`;t=`mediump`}return t===`mediump`&&e.getShaderPrecisionFormat(e.VERTEX_SHADER,e.MEDIUM_FLOAT).precision>0&&e.getShaderPrecisionFormat(e.FRAGMENT_SHADER,e.MEDIUM_FLOAT).precision>0?`mediump`:`lowp`}let l=n.precision===void 0?`highp`:n.precision,u=c(l);u!==l&&(V(`WebGLRenderer:`,l,`not supported, using`,u,`instead.`),l=u);let d=n.logarithmicDepthBuffer===!0,f=n.reversedDepthBuffer===!0&&t.has(`EXT_clip_control`);n.reversedDepthBuffer===!0&&f===!1&&V(`WebGLRenderer: Unable to use reversed depth buffer due to missing EXT_clip_control extension. Fallback to default depth buffer.`);let p=e.getParameter(e.MAX_TEXTURE_IMAGE_UNITS),m=e.getParameter(e.MAX_VERTEX_TEXTURE_IMAGE_UNITS),h=e.getParameter(e.MAX_TEXTURE_SIZE),g=e.getParameter(e.MAX_CUBE_MAP_TEXTURE_SIZE),_=e.getParameter(e.MAX_VERTEX_ATTRIBS),v=e.getParameter(e.MAX_VERTEX_UNIFORM_VECTORS),y=e.getParameter(e.MAX_VARYING_VECTORS),b=e.getParameter(e.MAX_FRAGMENT_UNIFORM_VECTORS),x=e.getParameter(e.MAX_SAMPLES),S=e.getParameter(e.SAMPLES);return{isWebGL2:!0,getMaxAnisotropy:a,getMaxPrecision:c,textureFormatReadable:o,textureTypeReadable:s,precision:l,logarithmicDepthBuffer:d,reversedDepthBuffer:f,maxTextures:p,maxVertexTextures:m,maxTextureSize:h,maxCubemapSize:g,maxAttributes:_,maxVertexUniforms:v,maxVaryings:y,maxFragmentUniforms:b,maxSamples:x,samples:S}}function Co(e){let t=this,n=null,r=0,i=!1,a=!1,o=new Hr,s=new K,c={value:null,needsUpdate:!1};this.uniform=c,this.numPlanes=0,this.numIntersection=0,this.init=function(e,t){let n=e.length!==0||t||r!==0||i;return i=t,r=e.length,n},this.beginShadows=function(){a=!0,u(null)},this.endShadows=function(){a=!1},this.setGlobalState=function(e,t){n=u(e,t,0)},this.setState=function(t,o,s){let d=t.clippingPlanes,f=t.clipIntersection,p=t.clipShadows,m=e.get(t);if(!i||d===null||d.length===0||a&&!p)a?u(null):l();else{let e=a?0:r,t=e*4,i=m.clippingState||null;c.value=i,i=u(d,o,t,s);for(let e=0;e!==t;++e)i[e]=n[e];m.clippingState=i,this.numIntersection=f?this.numPlanes:0,this.numPlanes+=e}};function l(){c.value!==n&&(c.value=n,c.needsUpdate=r>0),t.numPlanes=r,t.numIntersection=0}function u(e,n,r,i){let a=e===null?0:e.length,l=null;if(a!==0){if(l=c.value,i!==!0||l===null){let t=r+a*4,i=n.matrixWorldInverse;s.getNormalMatrix(i),(l===null||l.length<t)&&(l=new Float32Array(t));for(let t=0,n=r;t!==a;++t,n+=4)o.copy(e[t]).applyMatrix4(i,s),o.normal.toArray(l,n),l[n+3]=o.constant}c.value=l,c.needsUpdate=!0}return t.numPlanes=a,t.numIntersection=0,l}}var wo=4,To=6,Eo=20,Do=256,Oo=new Wa,ko=new Y,Ao=null,jo=0,Mo=0,No=!1,Po=new G,Fo=new G,Io=class{constructor(e){this._renderer=e,this._pingPongRenderTarget=null,this._lodMax=0,this._cubeSize=0,this._sizeLods=[],this._lodMeshes=[],this._backgroundBox=null,this._cubemapMaterial=null,this._equirectMaterial=null,this._blurMaterial=null,this._ggxMaterial=null}fromScene(e,t=0,n=.1,r=100,i={}){let{size:a=256,position:o=Po}=i;Ao=this._renderer.getRenderTarget(),jo=this._renderer.getActiveCubeFace(),Mo=this._renderer.getActiveMipmapLevel(),No=this._renderer.xr.enabled,this._renderer.xr.enabled=!1,this._setSize(a);let s=this._allocateTargets();return s.depthBuffer=!0,this._sceneToCubeUV(e,n,r,s,o),t>0&&this._blur(s,0,0,t),this._applyPMREM(s),this._cleanup(s),s}fromEquirectangular(e,t=null){return this._fromTexture(e,t)}fromCubemap(e,t=null){return this._fromTexture(e,t)}compileCubemapShader(){this._cubemapMaterial===null&&(this._cubemapMaterial=Uo(),this._compileMaterial(this._cubemapMaterial))}compileEquirectangularShader(){this._equirectMaterial===null&&(this._equirectMaterial=Ho(),this._compileMaterial(this._equirectMaterial))}dispose(){this._dispose(),this._cubemapMaterial!==null&&this._cubemapMaterial.dispose(),this._equirectMaterial!==null&&this._equirectMaterial.dispose(),this._backgroundBox!==null&&(this._backgroundBox.geometry.dispose(),this._backgroundBox.material.dispose())}_setSize(e){this._lodMax=Math.floor(Math.log2(e)),this._cubeSize=2**this._lodMax}_dispose(){this._blurMaterial!==null&&this._blurMaterial.dispose(),this._ggxMaterial!==null&&this._ggxMaterial.dispose(),this._pingPongRenderTarget!==null&&this._pingPongRenderTarget.dispose();for(let e=0;e<this._lodMeshes.length;e++)this._lodMeshes[e].geometry.dispose()}_cleanup(e){this._renderer.setRenderTarget(Ao,jo,Mo),this._renderer.xr.enabled=No,e.scissorTest=!1,zo(e,0,0,e.width,e.height)}_fromTexture(e,t){e.mapping===301||e.mapping===302?this._setSize(e.image.length===0?16:e.image[0].width||e.image[0].image.width):this._setSize(e.image.width/4),Ao=this._renderer.getRenderTarget(),jo=this._renderer.getActiveCubeFace(),Mo=this._renderer.getActiveMipmapLevel(),No=this._renderer.xr.enabled,this._renderer.xr.enabled=!1;let n=t||this._allocateTargets();return this._textureToCubeUV(e,n),this._applyPMREM(n),this._cleanup(n),n}_allocateTargets(){let e=3*Math.max(this._cubeSize,112),t=4*this._cubeSize,n={magFilter:x,minFilter:x,generateMipmaps:!1,type:j,format:ae,colorSpace:Ze,depthBuffer:!1},r=Ro(e,t,n);if(this._pingPongRenderTarget===null||this._pingPongRenderTarget.width!==e||this._pingPongRenderTarget.height!==t){this._pingPongRenderTarget!==null&&this._dispose(),this._pingPongRenderTarget=Ro(e,t,n);let{_lodMax:r}=this;({lodMeshes:this._lodMeshes,sizeLods:this._sizeLods}=Lo(r)),this._blurMaterial=Vo(r,e,t),this._ggxMaterial=Bo(r,e,t)}return r}_compileMaterial(e){let t=new ci(new Rr,e);this._renderer.compile(t,Oo)}_sceneToCubeUV(e,t,n,r,i){let a=new Ua(90,1,t,n),o=[1,-1,1,1,1,1],s=[1,1,1,-1,-1,-1],c=this._renderer,l=c.autoClear,u=c.toneMapping;c.getClearColor(ko),c.toneMapping=0,c.autoClear=!1,c.state.buffers.depth.getReversed()&&(c.setRenderTarget(r),c.clearDepth(),c.setRenderTarget(null)),this._backgroundBox===null&&(this._backgroundBox=new ci(new Xi,new Xr({name:`PMREM.Background`,side:1,depthWrite:!1,depthTest:!1})));let d=this._backgroundBox,f=d.material,p=!1,m=e.background;m?m.isColor&&(f.color.copy(m),e.background=null,p=!0):(f.color.copy(ko),p=!0);for(let t=0;t<6;t++){let n=t%3;n===0?(a.up.set(0,o[t],0),a.position.set(i.x,i.y,i.z),a.lookAt(i.x+s[t],i.y,i.z)):n===1?(a.up.set(0,0,o[t]),a.position.set(i.x,i.y,i.z),a.lookAt(i.x,i.y+s[t],i.z)):(a.up.set(0,o[t],0),a.position.set(i.x,i.y,i.z),a.lookAt(i.x,i.y,i.z+s[t]));let l=this._cubeSize;zo(r,n*l,t>2?l:0,l,l),c.setRenderTarget(r),p&&c.render(d,a),c.render(e,a)}c.toneMapping=u,c.autoClear=l,e.background=m}_textureToCubeUV(e,t){let n=this._renderer,r=e.mapping===301||e.mapping===302;r?(this._cubemapMaterial===null&&(this._cubemapMaterial=Uo()),this._cubemapMaterial.uniforms.flipEnvMap.value=e.isRenderTargetTexture===!1?-1:1):this._equirectMaterial===null&&(this._equirectMaterial=Ho());let i=r?this._cubemapMaterial:this._equirectMaterial,a=this._lodMeshes[0];a.material=i;let o=i.uniforms;o.envMap.value=e;let s=this._cubeSize;zo(t,0,0,3*s,2*s),n.setRenderTarget(t),n.render(a,Oo)}_applyPMREM(e){let t=this._renderer,n=t.autoClear;t.autoClear=!1;let r=this._lodMeshes.length;for(let t=1;t<r;t++)this._applyGGXFilter(e,t-1,t);t.autoClear=n}_applyGGXFilter(e,t,n){let r=this._renderer,i=this._pingPongRenderTarget,a=this._ggxMaterial,o=this._lodMeshes[n];o.material=a;let s=a.uniforms,c=n/(this._lodMeshes.length-1),l=t/(this._lodMeshes.length-1),u=Math.sqrt(c*c-l*l)*(c*1.25),{_lodMax:d}=this,f=this._sizeLods[n],p=3*f*(n>d-wo?n-d+wo:0),m=4*(this._cubeSize-f);s.envMap.value=e.texture,s.roughness.value=u,s.mipInt.value=d-t,zo(i,p,m,3*f,2*f),r.setRenderTarget(i),r.render(o,Oo),s.envMap.value=i.texture,s.roughness.value=0,s.mipInt.value=d-n,zo(e,p,m,3*f,2*f),r.setRenderTarget(e),r.render(o,Oo)}_blur(e,t,n,r){let i=this._pingPongRenderTarget,a=Math.min(r,Math.PI)/Math.SQRT2;this._blurPass(e,i,t,n,a),this._blurPass(i,e,n,n,a)}_blurPass(e,t,n,r,i){let a=this._renderer,o=this._blurMaterial,s=this._lodMeshes[r];s.material=o;let c=o.uniforms;c.envMap.value=e.texture,c.sigma.value=i,c.mipInt.value=this._lodMax-n;let l=this._sizeLods[r];zo(t,3*l*(r>this._lodMax-wo?r-this._lodMax+wo:0),4*(this._cubeSize-l),3*l,2*l),a.setRenderTarget(t),a.render(s,Oo)}};function Lo(e){let t=[],n=[],r=e,i=e-wo+1+To;for(let e=0;e<i;e++){let e=2**r;t.push(e);let i=1/(e-2),a=-i,o=1+i,s=[a,a,o,a,o,o,a,a,o,o,a,o],c=new Float32Array(108),l=new Float32Array(108);for(let e=0;e<6;e++){let t=e%3*2/3-1,n=e>2?0:-1,r=[t,n,0,t+2/3,n,0,t+2/3,n+1,0,t,n,0,t+2/3,n+1,0,t,n+1,0];c.set(r,18*e);for(let t=0;t<6;t++){let n=s[t*2]*2-1,r=s[t*2+1]*2-1;e===0?Fo.set(1,r,n):e===1?Fo.set(-n,1,-r):e===2?Fo.set(-n,r,1):e===3?Fo.set(-1,r,-n):e===4?Fo.set(-n,-1,r):Fo.set(n,r,-1),Fo.toArray(l,(e*6+t)*3)}}let u=new Rr;u.setAttribute(`position`,new X(c,3)),u.setAttribute(`outputDirection`,new X(l,3)),n.push(new ci(u,null)),r>wo&&r--}return{lodMeshes:n,sizeLods:t}}function Ro(e,t,n){let r=new on(e,t,n);return r.texture.mapping=306,r.texture.name=`PMREM.cubeUv`,r.scissorTest=!0,r}function zo(e,t,n,r,i){e.viewport.set(t,n,r,i),e.scissor.set(t,n,r,i)}function Bo(e,t,n){return new fa({name:`PMREMGGXConvolution`,defines:{GGX_SAMPLES:Do,CUBEUV_TEXEL_WIDTH:1/t,CUBEUV_TEXEL_HEIGHT:1/n,CUBEUV_MAX_MIP:`${e}.0`},uniforms:{envMap:{value:null},roughness:{value:0},mipInt:{value:0}},vertexShader:Wo(),fragmentShader:`

			precision highp float;
			precision highp int;

			varying vec3 vOutputDirection;

			uniform sampler2D envMap;
			uniform float roughness;
			uniform float mipInt;

			#define ENVMAP_TYPE_CUBE_UV
			#include <cube_uv_reflection_fragment>

			#define PI 3.14159265359

			// Van der Corput radical inverse
			float radicalInverse_VdC(uint bits) {
				bits = (bits << 16u) | (bits >> 16u);
				bits = ((bits & 0x55555555u) << 1u) | ((bits & 0xAAAAAAAAu) >> 1u);
				bits = ((bits & 0x33333333u) << 2u) | ((bits & 0xCCCCCCCCu) >> 2u);
				bits = ((bits & 0x0F0F0F0Fu) << 4u) | ((bits & 0xF0F0F0F0u) >> 4u);
				bits = ((bits & 0x00FF00FFu) << 8u) | ((bits & 0xFF00FF00u) >> 8u);
				return float(bits) * 2.3283064365386963e-10; // / 0x100000000
			}

			// Hammersley sequence
			vec2 hammersley(uint i, uint N) {
				return vec2(float(i) / float(N), radicalInverse_VdC(i));
			}

			// GGX VNDF importance sampling (Eric Heitz 2018)
			// "Sampling the GGX Distribution of Visible Normals"
			// https://jcgt.org/published/0007/04/01/
			vec3 importanceSampleGGX_VNDF(vec2 Xi, vec3 V, float roughness) {
				float alpha = roughness * roughness;

				// Section 4.1: Orthonormal basis
				vec3 T1 = vec3(1.0, 0.0, 0.0);
				vec3 T2 = cross(V, T1);

				// Section 4.2: Parameterization of projected area
				float r = sqrt(Xi.x);
				float phi = 2.0 * PI * Xi.y;
				float t1 = r * cos(phi);
				float t2 = r * sin(phi);
				float s = 0.5 * (1.0 + V.z);
				t2 = (1.0 - s) * sqrt(1.0 - t1 * t1) + s * t2;

				// Section 4.3: Reprojection onto hemisphere
				vec3 Nh = t1 * T1 + t2 * T2 + sqrt(max(0.0, 1.0 - t1 * t1 - t2 * t2)) * V;

				// Section 3.4: Transform back to ellipsoid configuration
				return normalize(vec3(alpha * Nh.x, alpha * Nh.y, max(0.0, Nh.z)));
			}

			void main() {
				vec3 N = normalize(vOutputDirection);
				vec3 V = N; // Assume view direction equals normal for pre-filtering

				vec3 prefilteredColor = vec3(0.0);
				float totalWeight = 0.0;

				// For very low roughness, just sample the environment directly
				if (roughness < 0.001) {
					gl_FragColor = vec4(bilinearCubeUV(envMap, N, mipInt), 1.0);
					return;
				}

				// Tangent space basis for VNDF sampling
				vec3 up = abs(N.z) < 0.999 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
				vec3 tangent = normalize(cross(up, N));
				vec3 bitangent = cross(N, tangent);

				for(uint i = 0u; i < uint(GGX_SAMPLES); i++) {
					vec2 Xi = hammersley(i, uint(GGX_SAMPLES));

					// For PMREM, V = N, so in tangent space V is always (0, 0, 1)
					vec3 H_tangent = importanceSampleGGX_VNDF(Xi, vec3(0.0, 0.0, 1.0), roughness);

					// Transform H back to world space
					vec3 H = normalize(tangent * H_tangent.x + bitangent * H_tangent.y + N * H_tangent.z);
					vec3 L = normalize(2.0 * dot(V, H) * H - V);

					float NdotL = max(dot(N, L), 0.0);

					if(NdotL > 0.0) {
						// Sample environment at fixed mip level
						// VNDF importance sampling handles the distribution filtering
						vec3 sampleColor = bilinearCubeUV(envMap, L, mipInt);

						// Weight by NdotL for the split-sum approximation
						// VNDF PDF naturally accounts for the visible microfacet distribution
						prefilteredColor += sampleColor * NdotL;
						totalWeight += NdotL;
					}
				}

				if (totalWeight > 0.0) {
					prefilteredColor = prefilteredColor / totalWeight;
				}

				gl_FragColor = vec4(prefilteredColor, 1.0);
			}
		`,blending:0,depthTest:!1,depthWrite:!1})}function Vo(e,t,n){return new fa({name:`SphericalGaussianBlur`,defines:{SAMPLES:Eo,CUBEUV_TEXEL_WIDTH:1/t,CUBEUV_TEXEL_HEIGHT:1/n,CUBEUV_MAX_MIP:`${e}.0`},uniforms:{envMap:{value:null},sigma:{value:0},mipInt:{value:0}},vertexShader:Wo(),fragmentShader:`

			precision highp float;
			precision highp int;

			varying vec3 vOutputDirection;

			uniform sampler2D envMap;
			uniform float sigma;
			uniform float mipInt;

			#define ENVMAP_TYPE_CUBE_UV
			#include <cube_uv_reflection_fragment>

			#define PI 3.14159265359
			#define GOLDEN_ANGLE 2.39996322973

			void main() {

				if ( sigma == 0.0 ) {

					gl_FragColor = vec4( bilinearCubeUV( envMap, vOutputDirection, mipInt ), 1.0 );
					return;

				}

				vec3 outputDirection = normalize( vOutputDirection );

				vec3 up = abs( outputDirection.z ) < 0.999 ? vec3( 0.0, 0.0, 1.0 ) : vec3( 1.0, 0.0, 0.0 );
				vec3 tangent = normalize( cross( up, outputDirection ) );
				vec3 bitangent = cross( outputDirection, tangent );

				// Truncate the kernel at three standard deviations or at the antipode.
				float thetaMax = min( 3.0 * sigma, PI );
				float truncation = 1.0 - exp( - 0.5 * thetaMax * thetaMax / ( sigma * sigma ) );

				vec3 accumColor = vec3( 0.0 );
				float accumWeight = 0.0;

				for ( int i = 0; i < SAMPLES; i ++ ) {

					// Stratified inverse-CDF sampling of the Gaussian, placed on a golden-angle spiral.
					float stratum = ( float( i ) + 0.5 ) / float( SAMPLES );
					float theta = sigma * sqrt( - 2.0 * log( 1.0 - stratum * truncation ) );
					float phi = float( i ) * GOLDEN_ANGLE;

					vec3 offset = cos( phi ) * tangent + sin( phi ) * bitangent;
					vec3 sampleDirection = cos( theta ) * outputDirection + sin( theta ) * offset;

					// Correct the planar sample density to solid angle.
					float weight = sin( theta ) / theta;

					accumColor += weight * bilinearCubeUV( envMap, sampleDirection, mipInt );
					accumWeight += weight;

				}

				gl_FragColor = vec4( accumColor / accumWeight, 1.0 );

			}
		`,blending:0,depthTest:!1,depthWrite:!1})}function Ho(){return new fa({name:`EquirectangularToCubeUV`,uniforms:{envMap:{value:null}},vertexShader:Wo(),fragmentShader:`

			precision mediump float;
			precision mediump int;

			varying vec3 vOutputDirection;

			uniform sampler2D envMap;

			#include <common>

			void main() {

				vec3 outputDirection = normalize( vOutputDirection );
				vec2 uv = equirectUv( outputDirection );

				gl_FragColor = vec4( texture2D ( envMap, uv ).rgb, 1.0 );

			}
		`,blending:0,depthTest:!1,depthWrite:!1})}function Uo(){return new fa({name:`CubemapToCubeUV`,uniforms:{envMap:{value:null},flipEnvMap:{value:-1}},vertexShader:Wo(),fragmentShader:`

			precision mediump float;
			precision mediump int;

			uniform float flipEnvMap;

			varying vec3 vOutputDirection;

			uniform samplerCube envMap;

			void main() {

				gl_FragColor = textureCube( envMap, vec3( flipEnvMap * vOutputDirection.x, vOutputDirection.yz ) );

			}
		`,blending:0,depthTest:!1,depthWrite:!1})}function Wo(){return`

		precision mediump float;
		precision mediump int;

		attribute vec3 outputDirection;

		varying vec3 vOutputDirection;

		void main() {

			vOutputDirection = outputDirection;
			gl_Position = vec4( position, 1.0 );

		}
	`}var Go=class extends on{constructor(e=1,t={}){super(e,e,t),this.isWebGLCubeRenderTarget=!0;let n={width:e,height:e,depth:1},r=[n,n,n,n,n,n];this.texture=new Gi(r),this._setTextureOptions(t),this.texture.isRenderTargetTexture=!0}fromEquirectangularTexture(e,t){this.texture.type=t.type,this.texture.colorSpace=t.colorSpace,this.texture.generateMipmaps=t.generateMipmaps,this.texture.minFilter=t.minFilter,this.texture.magFilter=t.magFilter;let n={uniforms:{tEquirect:{value:null}},vertexShader:`

				varying vec3 vWorldDirection;

				vec3 transformDirection( in vec3 dir, in mat4 matrix ) {

					return normalize( ( matrix * vec4( dir, 0.0 ) ).xyz );

				}

				void main() {

					vWorldDirection = transformDirection( position, modelMatrix );

					#include <begin_vertex>
					#include <project_vertex>

				}
			`,fragmentShader:`

				uniform sampler2D tEquirect;

				varying vec3 vWorldDirection;

				#include <common>

				void main() {

					vec3 direction = normalize( vWorldDirection );

					vec2 sampleUV = equirectUv( direction );

					gl_FragColor = texture2D( tEquirect, sampleUV );

				}
			`},r=new Xi(5,5,5),i=new fa({name:`CubemapFromEquirect`,uniforms:ia(n.uniforms),vertexShader:n.vertexShader,fragmentShader:n.fragmentShader,side:1,blending:0});i.uniforms.tEquirect.value=t;let a=new ci(r,i),o=t.minFilter;return t.minFilter===1008&&(t.minFilter=x),new qa(1,10,this).update(e,a),t.minFilter=o,a.geometry.dispose(),a.material.dispose(),this}clear(e,t=!0,n=!0,r=!0){let i=e.getRenderTarget();for(let i=0;i<6;i++)e.setRenderTarget(this,i),e.clear(t,n,r);e.setRenderTarget(i)}};function Ko(e){let t=new WeakMap,n=new WeakMap,r=null;function i(e,t=!1){return e==null?null:t?o(e):a(e)}function a(n){if(n&&n.isTexture){let r=n.mapping;if(r===303||r===304){if(t.has(n)){let e=t.get(n).texture;return s(e,n.mapping)}{let r=n.image;if(r&&r.height>0){let i=new Go(r.height);return i.fromEquirectangularTexture(e,n),t.set(n,i),n.addEventListener(`dispose`,l),s(i.texture,n.mapping)}return null}}}return n}function o(t){if(t&&t.isTexture){let i=t.mapping,a=i===303||i===304,o=i===301||i===302;if(a||o){let i=n.get(t),s=i===void 0?0:i.texture.pmremVersion;if(t.isRenderTargetTexture&&t.pmremVersion!==s)return r===null&&(r=new Io(e)),i=a?r.fromEquirectangular(t,i):r.fromCubemap(t,i),i.texture.pmremVersion=t.pmremVersion,n.set(t,i),i.texture;if(i!==void 0)return i.texture;{let s=t.image;return a&&s&&s.height>0||o&&s&&c(s)?(r===null&&(r=new Io(e)),i=a?r.fromEquirectangular(t):r.fromCubemap(t),i.texture.pmremVersion=t.pmremVersion,n.set(t,i),t.addEventListener(`dispose`,u),i.texture):null}}}return t}function s(e,t){return t===303?e.mapping=301:t===304&&(e.mapping=302),e}function c(e){let t=0;for(let n=0;n<6;n++)e[n]!==void 0&&t++;return t===6}function l(e){let n=e.target;n.removeEventListener(`dispose`,l);let r=t.get(n);r!==void 0&&(t.delete(n),r.dispose())}function u(e){let t=e.target;t.removeEventListener(`dispose`,u);let r=n.get(t);r!==void 0&&(n.delete(t),r.dispose())}function d(){t=new WeakMap,n=new WeakMap,r!==null&&(r.dispose(),r=null)}return{get:i,dispose:d}}function qo(e){let t={};function n(n){if(t[n]!==void 0)return t[n];let r=e.getExtension(n);return t[n]=r,r}return{has:function(e){return n(e)!==null},init:function(){n(`EXT_color_buffer_float`),n(`WEBGL_clip_cull_distance`),n(`OES_texture_float_linear`),n(`EXT_color_buffer_half_float`),n(`WEBGL_multisampled_render_to_texture`),n(`WEBGL_render_shared_exponent`)},get:function(e){let t=n(e);return t===null&&ut(`WebGLRenderer: `+e+` extension not supported.`),t}}}function Jo(e,t,n,r){let i={},a=new WeakMap;function o(e){let s=e.target;s.index!==null&&t.remove(s.index);for(let e in s.attributes)t.remove(s.attributes[e]);s.removeEventListener(`dispose`,o),delete i[s.id];let c=a.get(s);c&&(t.remove(c),a.delete(s)),r.releaseStatesOfGeometry(s),s.isInstancedBufferGeometry===!0&&delete s._maxInstanceCount,n.memory.geometries--}function s(e,t){return i[t.id]===!0||(t.addEventListener(`dispose`,o),i[t.id]=!0,n.memory.geometries++),t}function c(n){let r=n.attributes;for(let n in r)t.update(r[n],e.ARRAY_BUFFER)}function l(e){let n=[],r=e.index,i=e.attributes.position,o=0;if(i===void 0)return;if(r!==null){let e=r.array;o=r.version;for(let t=0,r=e.length;t<r;t+=3){let r=e[t+0],i=e[t+1],a=e[t+2];n.push(r,i,i,a,a,r)}}else{let e=i.array;o=i.version;for(let t=0,r=e.length/3-1;t<r;t+=3){let e=t+0,r=t+1,i=t+2;n.push(e,r,r,i,i,e)}}let s=new(i.count>=65535?Tr:wr)(n,1);s.version=o;let c=a.get(e);c&&t.remove(c),a.set(e,s)}function u(e){let t=a.get(e);if(t){let n=e.index;n!==null&&t.version<n.version&&l(e)}else l(e);return a.get(e)}return{get:s,update:c,getWireframeAttribute:u}}function Yo(e,t,n){let r;function i(e){r=e}let a,o;function s(e){a=e.type,o=e.bytesPerElement}function c(t,i){e.drawElements(r,i,a,t*o),n.update(i,r,1)}function l(t,i,s){s!==0&&(e.drawElementsInstanced(r,i,a,t*o,s),n.update(i,r,s))}function u(e,i,o){if(o===0)return;t.get(`WEBGL_multi_draw`).multiDrawElementsWEBGL(r,i,0,a,e,0,o);let s=0;for(let e=0;e<o;e++)s+=i[e];n.update(s,r,1)}this.setMode=i,this.setIndex=s,this.render=c,this.renderInstances=l,this.renderMultiDraw=u}function Xo(e){let t={geometries:0,textures:0},n={frame:0,calls:0,triangles:0,points:0,lines:0};function r(t,r,i){switch(n.calls++,r){case e.TRIANGLES:n.triangles+=t/3*i;break;case e.LINES:n.lines+=t/2*i;break;case e.LINE_STRIP:n.lines+=i*(t-1);break;case e.LINE_LOOP:n.lines+=i*t;break;case e.POINTS:n.points+=i*t;break;default:H(`WebGLInfo: Unknown draw mode:`,r)}}function i(){n.calls=0,n.triangles=0,n.points=0,n.lines=0}return{memory:t,render:n,programs:null,autoReset:!0,reset:i,update:r}}function Zo(e,t,n){let r=new WeakMap,i=new rn;function a(a,o,s){let c=a.morphTargetInfluences,l=o.morphAttributes.position||o.morphAttributes.normal||o.morphAttributes.color,u=l===void 0?0:l.length,d=r.get(o);if(d===void 0||d.count!==u){d!==void 0&&d.texture.dispose();let e=o.morphAttributes.position!==void 0,n=o.morphAttributes.normal!==void 0,a=o.morphAttributes.color!==void 0,s=o.morphAttributes.position||[],c=o.morphAttributes.normal||[],l=o.morphAttributes.color||[],f=0;e===!0&&(f=1),n===!0&&(f=2),a===!0&&(f=3);let p=o.attributes.position.count*f,m=1;p>t.maxTextureSize&&(m=Math.ceil(p/t.maxTextureSize),p=t.maxTextureSize);let h=new Float32Array(p*m*4*u),g=new sn(h,p,m,u);g.type=A,g.needsUpdate=!0;let _=f*4;for(let t=0;t<u;t++){let r=s[t],o=c[t],u=l[t],d=p*m*4*t;for(let t=0;t<r.count;t++){let s=t*_;e===!0&&(i.fromBufferAttribute(r,t),h[d+s+0]=i.x,h[d+s+1]=i.y,h[d+s+2]=i.z,h[d+s+3]=0),n===!0&&(i.fromBufferAttribute(o,t),h[d+s+4]=i.x,h[d+s+5]=i.y,h[d+s+6]=i.z,h[d+s+7]=0),a===!0&&(i.fromBufferAttribute(u,t),h[d+s+8]=i.x,h[d+s+9]=i.y,h[d+s+10]=i.z,h[d+s+11]=u.itemSize===4?i.w:1)}}d={count:u,texture:g,size:new W(p,m)},r.set(o,d);function v(){g.dispose(),r.delete(o),o.removeEventListener(`dispose`,v)}o.addEventListener(`dispose`,v)}if(a.isInstancedMesh===!0&&a.morphTexture!==null)s.getUniforms().setValue(e,`morphTexture`,a.morphTexture,n);else{let t=0;for(let e=0;e<c.length;e++)t+=c[e];let n=o.morphTargetsRelative?1:1-t;s.getUniforms().setValue(e,`morphTargetBaseInfluence`,n),s.getUniforms().setValue(e,`morphTargetInfluences`,c)}s.getUniforms().setValue(e,`morphTargetsTexture`,d.texture,n),s.getUniforms().setValue(e,`morphTargetsTextureSize`,d.size)}return{update:a}}function Qo(e,t,n,r,i){let a=new WeakMap;function o(r){let o=i.render.frame,s=r.geometry,l=t.get(r,s);if(a.get(l)!==o&&(t.update(l),a.set(l,o)),r.isInstancedMesh&&(r.hasEventListener(`dispose`,c)===!1&&r.addEventListener(`dispose`,c),a.get(r)!==o&&(n.update(r.instanceMatrix,e.ARRAY_BUFFER),r.instanceColor!==null&&n.update(r.instanceColor,e.ARRAY_BUFFER),a.set(r,o))),r.isSkinnedMesh){let e=r.skeleton;a.get(e)!==o&&(e.update(),a.set(e,o))}return l}function s(){a=new WeakMap}function c(e){let t=e.target;t.removeEventListener(`dispose`,c),r.releaseStatesOfObject(t),n.remove(t.instanceMatrix),t.instanceColor!==null&&n.remove(t.instanceColor)}return{update:o,dispose:s}}var $o={1:`LINEAR_TONE_MAPPING`,2:`REINHARD_TONE_MAPPING`,3:`CINEON_TONE_MAPPING`,4:`ACES_FILMIC_TONE_MAPPING`,6:`AGX_TONE_MAPPING`,7:`NEUTRAL_TONE_MAPPING`,5:`CUSTOM_TONE_MAPPING`};function es(e,t,n,r,i,a){let o=new on(t,n,{type:e,depthBuffer:i,stencilBuffer:a,samples:r?4:0,storeMultisampledDepthBuffer:!1,storeMultisampledStencilBuffer:!1,resolveDepthBuffer:!1,resolveStencilBuffer:!1}),s=null,c=null,l=new Rr;l.setAttribute(`position`,new Er([-1,3,0,-1,-1,0,3,-1,0],3)),l.setAttribute(`uv`,new Er([0,2,0,0,2,0],2));let u=new pa({uniforms:{tDiffuse:{value:null}},vertexShader:`
			precision highp float;

			uniform mat4 modelViewMatrix;
			uniform mat4 projectionMatrix;

			attribute vec3 position;
			attribute vec2 uv;

			varying vec2 vUv;

			void main() {
				vUv = uv;
				gl_Position = projectionMatrix * modelViewMatrix * vec4( position, 1.0 );
			}`,fragmentShader:`
			precision highp float;

			uniform sampler2D tDiffuse;

			varying vec2 vUv;

			#include <tonemapping_pars_fragment>
			#include <colorspace_pars_fragment>

			void main() {
				gl_FragColor = texture2D( tDiffuse, vUv );

				#ifdef LINEAR_TONE_MAPPING
					gl_FragColor.rgb = LinearToneMapping( gl_FragColor.rgb );
				#elif defined( REINHARD_TONE_MAPPING )
					gl_FragColor.rgb = ReinhardToneMapping( gl_FragColor.rgb );
				#elif defined( CINEON_TONE_MAPPING )
					gl_FragColor.rgb = CineonToneMapping( gl_FragColor.rgb );
				#elif defined( ACES_FILMIC_TONE_MAPPING )
					gl_FragColor.rgb = ACESFilmicToneMapping( gl_FragColor.rgb );
				#elif defined( AGX_TONE_MAPPING )
					gl_FragColor.rgb = AgXToneMapping( gl_FragColor.rgb );
				#elif defined( NEUTRAL_TONE_MAPPING )
					gl_FragColor.rgb = NeutralToneMapping( gl_FragColor.rgb );
				#elif defined( CUSTOM_TONE_MAPPING )
					gl_FragColor.rgb = CustomToneMapping( gl_FragColor.rgb );
				#endif

				#ifdef SRGB_TRANSFER
					gl_FragColor = sRGBTransferOETF( gl_FragColor );
				#endif
			}`,depthTest:!1,depthWrite:!1}),d=new ci(l,u),f=new Wa(-1,1,1,-1,0,1),p=null,m=null,h=!1,g,_=null,v=[],y=!1;this.setSize=function(e,t){o.setSize(e,t),s!==null&&s.setSize(e,t),c!==null&&c.setSize(e,t);for(let n=0;n<v.length;n++){let r=v[n];r.setSize&&r.setSize(e,t)}},this.setEffects=function(e){v=e,y=v.length>0&&v[0].isRenderPass===!0;let t=o.width,n=o.height;v.length>0&&s===null&&(s=new on(t,n,{type:j,depthBuffer:!1,stencilBuffer:!1}),c=new on(t,n,{type:j,depthBuffer:!1,stencilBuffer:!1}));for(let e=0;e<v.length;e++){let r=v[e];r.setSize&&r.setSize(t,n)}},this.begin=function(e,t){if(h||e.toneMapping===0&&v.length===0)return!1;if(_=t,t!==null){let e=t.width,n=t.height;(o.width!==e||o.height!==n)&&this.setSize(e,n)}return y===!1&&e.setRenderTarget(o),g=e.toneMapping,e.toneMapping=0,!0},this.hasRenderPass=function(){return y},this.end=function(e,t){e.toneMapping=g,h=!0;let n=o,r=s;for(let i=0;i<v.length;i++){let a=v[i];a.enabled!==!1&&(a.render(e,r,n,t),a.needsSwap!==!1&&(n=r,r=r===s?c:s))}if(p!==e.outputColorSpace||m!==e.toneMapping){p=e.outputColorSpace,m=e.toneMapping,u.defines={},q.getTransfer(p)===`srgb`&&(u.defines.SRGB_TRANSFER=``);let t=$o[m];t&&(u.defines[t]=``),u.needsUpdate=!0}u.uniforms.tDiffuse.value=n.texture,e.setRenderTarget(_),e.render(d,f),_=null,h=!1},this.isCompositing=function(){return h},this.dispose=function(){o.dispose(),s!==null&&s.dispose(),c!==null&&c.dispose(),l.dispose(),u.dispose()}}var ts=new nn,ns=new qi(1,1),rs=new sn,is=new cn,as=new Gi,os=[],ss=[],cs=new Float32Array(16),ls=new Float32Array(9),us=new Float32Array(4);function ds(e,t,n){let r=e[0];if(r<=0||r>0)return e;let i=t*n,a=os[i];if(a===void 0&&(a=new Float32Array(i),os[i]=a),t!==0){r.toArray(a,0);for(let r=1,i=0;r!==t;++r)i+=n,e[r].toArray(a,i)}return a}function fs(e,t){if(e.length!==t.length)return!1;for(let n=0,r=e.length;n<r;n++)if(e[n]!==t[n])return!1;return!0}function ps(e,t){for(let n=0,r=t.length;n<r;n++)e[n]=t[n]}function ms(e,t){let n=ss[t];n===void 0&&(n=new Int32Array(t),ss[t]=n);for(let r=0;r!==t;++r)n[r]=e.allocateTextureUnit();return n}function hs(e,t){let n=this.cache;n[0]!==t&&(e.uniform1f(this.addr,t),n[0]=t)}function gs(e,t){let n=this.cache;if(t.x!==void 0)(n[0]!==t.x||n[1]!==t.y)&&(e.uniform2f(this.addr,t.x,t.y),n[0]=t.x,n[1]=t.y);else{if(fs(n,t))return;e.uniform2fv(this.addr,t),ps(n,t)}}function _s(e,t){let n=this.cache;if(t.x!==void 0)(n[0]!==t.x||n[1]!==t.y||n[2]!==t.z)&&(e.uniform3f(this.addr,t.x,t.y,t.z),n[0]=t.x,n[1]=t.y,n[2]=t.z);else if(t.r!==void 0)(n[0]!==t.r||n[1]!==t.g||n[2]!==t.b)&&(e.uniform3f(this.addr,t.r,t.g,t.b),n[0]=t.r,n[1]=t.g,n[2]=t.b);else{if(fs(n,t))return;e.uniform3fv(this.addr,t),ps(n,t)}}function vs(e,t){let n=this.cache;if(t.x!==void 0)(n[0]!==t.x||n[1]!==t.y||n[2]!==t.z||n[3]!==t.w)&&(e.uniform4f(this.addr,t.x,t.y,t.z,t.w),n[0]=t.x,n[1]=t.y,n[2]=t.z,n[3]=t.w);else{if(fs(n,t))return;e.uniform4fv(this.addr,t),ps(n,t)}}function ys(e,t){let n=this.cache,r=t.elements;if(r===void 0){if(fs(n,t))return;e.uniformMatrix2fv(this.addr,!1,t),ps(n,t)}else{if(fs(n,r))return;us.set(r),e.uniformMatrix2fv(this.addr,!1,us),ps(n,r)}}function bs(e,t){let n=this.cache,r=t.elements;if(r===void 0){if(fs(n,t))return;e.uniformMatrix3fv(this.addr,!1,t),ps(n,t)}else{if(fs(n,r))return;ls.set(r),e.uniformMatrix3fv(this.addr,!1,ls),ps(n,r)}}function xs(e,t){let n=this.cache,r=t.elements;if(r===void 0){if(fs(n,t))return;e.uniformMatrix4fv(this.addr,!1,t),ps(n,t)}else{if(fs(n,r))return;cs.set(r),e.uniformMatrix4fv(this.addr,!1,cs),ps(n,r)}}function Ss(e,t){let n=this.cache;n[0]!==t&&(e.uniform1i(this.addr,t),n[0]=t)}function Cs(e,t){let n=this.cache;if(t.x!==void 0)(n[0]!==t.x||n[1]!==t.y)&&(e.uniform2i(this.addr,t.x,t.y),n[0]=t.x,n[1]=t.y);else{if(fs(n,t))return;e.uniform2iv(this.addr,t),ps(n,t)}}function ws(e,t){let n=this.cache;if(t.x!==void 0)(n[0]!==t.x||n[1]!==t.y||n[2]!==t.z)&&(e.uniform3i(this.addr,t.x,t.y,t.z),n[0]=t.x,n[1]=t.y,n[2]=t.z);else{if(fs(n,t))return;e.uniform3iv(this.addr,t),ps(n,t)}}function Ts(e,t){let n=this.cache;if(t.x!==void 0)(n[0]!==t.x||n[1]!==t.y||n[2]!==t.z||n[3]!==t.w)&&(e.uniform4i(this.addr,t.x,t.y,t.z,t.w),n[0]=t.x,n[1]=t.y,n[2]=t.z,n[3]=t.w);else{if(fs(n,t))return;e.uniform4iv(this.addr,t),ps(n,t)}}function Es(e,t){let n=this.cache;n[0]!==t&&(e.uniform1ui(this.addr,t),n[0]=t)}function Ds(e,t){let n=this.cache;if(t.x!==void 0)(n[0]!==t.x||n[1]!==t.y)&&(e.uniform2ui(this.addr,t.x,t.y),n[0]=t.x,n[1]=t.y);else{if(fs(n,t))return;e.uniform2uiv(this.addr,t),ps(n,t)}}function Os(e,t){let n=this.cache;if(t.x!==void 0)(n[0]!==t.x||n[1]!==t.y||n[2]!==t.z)&&(e.uniform3ui(this.addr,t.x,t.y,t.z),n[0]=t.x,n[1]=t.y,n[2]=t.z);else{if(fs(n,t))return;e.uniform3uiv(this.addr,t),ps(n,t)}}function ks(e,t){let n=this.cache;if(t.x!==void 0)(n[0]!==t.x||n[1]!==t.y||n[2]!==t.z||n[3]!==t.w)&&(e.uniform4ui(this.addr,t.x,t.y,t.z,t.w),n[0]=t.x,n[1]=t.y,n[2]=t.z,n[3]=t.w);else{if(fs(n,t))return;e.uniform4uiv(this.addr,t),ps(n,t)}}function As(e,t,n){let r=this.cache,i=n.allocateTextureUnit();r[0]!==i&&(e.uniform1i(this.addr,i),r[0]=i);let a;this.type===e.SAMPLER_2D_SHADOW?(ns.compareFunction=n.isReversedDepthBuffer()?518:515,a=ns):a=ts,n.setTexture2D(t||a,i)}function js(e,t,n){let r=this.cache,i=n.allocateTextureUnit();r[0]!==i&&(e.uniform1i(this.addr,i),r[0]=i),n.setTexture3D(t||is,i)}function Ms(e,t,n){let r=this.cache,i=n.allocateTextureUnit();r[0]!==i&&(e.uniform1i(this.addr,i),r[0]=i),n.setTextureCube(t||as,i)}function Ns(e,t,n){let r=this.cache,i=n.allocateTextureUnit();r[0]!==i&&(e.uniform1i(this.addr,i),r[0]=i),n.setTexture2DArray(t||rs,i)}function Ps(e){switch(e){case 5126:return hs;case 35664:return gs;case 35665:return _s;case 35666:return vs;case 35674:return ys;case 35675:return bs;case 35676:return xs;case 5124:case 35670:return Ss;case 35667:case 35671:return Cs;case 35668:case 35672:return ws;case 35669:case 35673:return Ts;case 5125:return Es;case 36294:return Ds;case 36295:return Os;case 36296:return ks;case 35678:case 36198:case 36298:case 36306:case 35682:return As;case 35679:case 36299:case 36307:return js;case 35680:case 36300:case 36308:case 36293:return Ms;case 36289:case 36303:case 36311:case 36292:return Ns}}function Fs(e,t){e.uniform1fv(this.addr,t)}function Is(e,t){let n=ds(t,this.size,2);e.uniform2fv(this.addr,n)}function Ls(e,t){let n=ds(t,this.size,3);e.uniform3fv(this.addr,n)}function Rs(e,t){let n=ds(t,this.size,4);e.uniform4fv(this.addr,n)}function zs(e,t){let n=ds(t,this.size,4);e.uniformMatrix2fv(this.addr,!1,n)}function Bs(e,t){let n=ds(t,this.size,9);e.uniformMatrix3fv(this.addr,!1,n)}function Vs(e,t){let n=ds(t,this.size,16);e.uniformMatrix4fv(this.addr,!1,n)}function Hs(e,t){e.uniform1iv(this.addr,t)}function Us(e,t){e.uniform2iv(this.addr,t)}function Ws(e,t){e.uniform3iv(this.addr,t)}function Gs(e,t){e.uniform4iv(this.addr,t)}function Ks(e,t){e.uniform1uiv(this.addr,t)}function qs(e,t){e.uniform2uiv(this.addr,t)}function Js(e,t){e.uniform3uiv(this.addr,t)}function Ys(e,t){e.uniform4uiv(this.addr,t)}function Xs(e,t,n){let r=this.cache,i=t.length,a=ms(n,i);fs(r,a)||(e.uniform1iv(this.addr,a),ps(r,a));let o;o=this.type===e.SAMPLER_2D_SHADOW?ns:ts;for(let e=0;e!==i;++e)n.setTexture2D(t[e]||o,a[e])}function Zs(e,t,n){let r=this.cache,i=t.length,a=ms(n,i);fs(r,a)||(e.uniform1iv(this.addr,a),ps(r,a));for(let e=0;e!==i;++e)n.setTexture3D(t[e]||is,a[e])}function Qs(e,t,n){let r=this.cache,i=t.length,a=ms(n,i);fs(r,a)||(e.uniform1iv(this.addr,a),ps(r,a));for(let e=0;e!==i;++e)n.setTextureCube(t[e]||as,a[e])}function $s(e,t,n){let r=this.cache,i=t.length,a=ms(n,i);fs(r,a)||(e.uniform1iv(this.addr,a),ps(r,a));for(let e=0;e!==i;++e)n.setTexture2DArray(t[e]||rs,a[e])}function ec(e){switch(e){case 5126:return Fs;case 35664:return Is;case 35665:return Ls;case 35666:return Rs;case 35674:return zs;case 35675:return Bs;case 35676:return Vs;case 5124:case 35670:return Hs;case 35667:case 35671:return Us;case 35668:case 35672:return Ws;case 35669:case 35673:return Gs;case 5125:return Ks;case 36294:return qs;case 36295:return Js;case 36296:return Ys;case 35678:case 36198:case 36298:case 36306:case 35682:return Xs;case 35679:case 36299:case 36307:return Zs;case 35680:case 36300:case 36308:case 36293:return Qs;case 36289:case 36303:case 36311:case 36292:return $s}}var tc=class{constructor(e,t,n){this.id=e,this.addr=n,this.cache=[],this.type=t.type,this.setValue=Ps(t.type)}},nc=class{constructor(e,t,n){this.id=e,this.addr=n,this.cache=[],this.type=t.type,this.size=t.size,this.setValue=ec(t.type)}},rc=class{constructor(e){this.id=e,this.seq=[],this.map={}}setValue(e,t,n){let r=this.seq;for(let i=0,a=r.length;i!==a;++i){let a=r[i];a.setValue(e,t[a.id],n)}}},ic=/(\w+)(\])?(\[|\.)?/g;function ac(e,t){e.seq.push(t),e.map[t.id]=t}function oc(e,t,n){let r=e.name,i=r.length;for(ic.lastIndex=0;;){let a=ic.exec(r),o=ic.lastIndex,s=a[1],c=a[2]===`]`,l=a[3];if(c&&(s|=0),l===void 0||l===`[`&&o+2===i){ac(n,l===void 0?new tc(s,e,t):new nc(s,e,t));break}{let e=n.map[s];e===void 0&&(e=new rc(s),ac(n,e)),n=e}}}var sc=class{constructor(e,t){this.seq=[],this.map={};let n=e.getProgramParameter(t,e.ACTIVE_UNIFORMS);for(let r=0;r<n;++r){let n=e.getActiveUniform(t,r);oc(n,e.getUniformLocation(t,n.name),this)}let r=[],i=[];for(let t of this.seq)t.type===e.SAMPLER_2D_SHADOW||t.type===e.SAMPLER_CUBE_SHADOW||t.type===e.SAMPLER_2D_ARRAY_SHADOW?r.push(t):i.push(t);r.length>0&&(this.seq=r.concat(i))}setValue(e,t,n,r){let i=this.map[t];i!==void 0&&i.setValue(e,n,r)}setOptional(e,t,n){let r=t[n];r!==void 0&&this.setValue(e,n,r)}static upload(e,t,n,r){for(let i=0,a=t.length;i!==a;++i){let a=t[i],o=n[a.id];o.needsUpdate!==!1&&a.setValue(e,o.value,r)}}static seqWithValue(e,t){let n=[];for(let r=0,i=e.length;r!==i;++r){let i=e[r];i.id in t&&n.push(i)}return n}};function cc(e,t,n){let r=e.createShader(t);return e.shaderSource(r,n),e.compileShader(r),r}var lc=37297,uc=0;function dc(e,t){let n=e.split(`
`),r=[],i=Math.max(t-6,0),a=Math.min(t+6,n.length);for(let e=i;e<a;e++){let i=e+1;r.push(`${i===t?`>`:` `} ${i}: ${n[e]}`)}return r.join(`
`)}var fc=new K;function pc(e){q._getMatrix(fc,q.workingColorSpace,e);let t=`mat3( ${fc.elements.map(e=>e.toFixed(4))} )`;switch(q.getTransfer(e)){case Qe:return[t,`LinearTransferOETF`];case $e:return[t,`sRGBTransferOETF`];default:return V(`WebGLProgram: Unsupported color space: `,e),[t,`LinearTransferOETF`]}}function mc(e,t,n){let r=e.getShaderParameter(t,e.COMPILE_STATUS),i=(e.getShaderInfoLog(t)||``).trim();if(r&&i===``)return``;let a=/ERROR: 0:(\d+)/.exec(i);if(a){let r=parseInt(a[1]);return n.toUpperCase()+`

`+i+`

`+dc(e.getShaderSource(t),r)}return i}function hc(e,t){let n=pc(t);return[`vec4 ${e}( vec4 value ) {`,`	return ${n[1]}( vec4( value.rgb * ${n[0]}, value.a ) );`,`}`].join(`
`)}var gc={1:`Linear`,2:`Reinhard`,3:`Cineon`,4:`ACESFilmic`,6:`AgX`,7:`Neutral`,5:`Custom`};function _c(e,t){let n=gc[t];return n===void 0?(V(`WebGLProgram: Unsupported toneMapping:`,t),`vec3 `+e+`( vec3 color ) { return LinearToneMapping( color ); }`):`vec3 `+e+`( vec3 color ) { return `+n+`ToneMapping( color ); }`}var vc=new G;function yc(){return q.getLuminanceCoefficients(vc),[`float luminance( const in vec3 rgb ) {`,`	const vec3 weights = vec3( ${vc.x.toFixed(4)}, ${vc.y.toFixed(4)}, ${vc.z.toFixed(4)} );`,`	return dot( weights, rgb );`,`}`].join(`
`)}function bc(e){return[e.extensionClipCullDistance?`#extension GL_ANGLE_clip_cull_distance : require`:``,e.extensionMultiDraw?`#extension GL_ANGLE_multi_draw : require`:``].filter(Cc).join(`
`)}function xc(e){let t=[];for(let n in e){let r=e[n];r!==!1&&t.push(`#define `+n+` `+r)}return t.join(`
`)}function Sc(e,t){let n={},r=e.getProgramParameter(t,e.ACTIVE_ATTRIBUTES);for(let i=0;i<r;i++){let r=e.getActiveAttrib(t,i),a=r.name,o=1;r.type===e.FLOAT_MAT2&&(o=2),r.type===e.FLOAT_MAT3&&(o=3),r.type===e.FLOAT_MAT4&&(o=4),n[a]={type:r.type,location:e.getAttribLocation(t,a),locationSize:o}}return n}function Cc(e){return e!==``}function wc(e,t){let n=t.numSpotLightShadows+t.numSpotLightMaps-t.numSpotLightShadowsWithMaps;return e.replace(/NUM_SUN_LIGHTS/g,t.numSunLights).replace(/NUM_DIR_LIGHTS/g,t.numDirLights).replace(/NUM_SPOT_LIGHTS/g,t.numSpotLights).replace(/NUM_SPOT_LIGHT_MAPS/g,t.numSpotLightMaps).replace(/NUM_SPOT_LIGHT_COORDS/g,n).replace(/NUM_RECT_AREA_LIGHTS/g,t.numRectAreaLights).replace(/NUM_POINT_LIGHTS/g,t.numPointLights).replace(/NUM_HEMI_LIGHTS/g,t.numHemiLights).replace(/NUM_SUN_LIGHT_SHADOWS/g,t.numSunLightShadows).replace(/NUM_DIR_LIGHT_SHADOWS/g,t.numDirLightShadows).replace(/NUM_SPOT_LIGHT_SHADOWS_WITH_MAPS/g,t.numSpotLightShadowsWithMaps).replace(/NUM_SPOT_LIGHT_SHADOWS/g,t.numSpotLightShadows).replace(/NUM_POINT_LIGHT_SHADOWS/g,t.numPointLightShadows)}function Tc(e,t){return e.replace(/NUM_CLIPPING_PLANES/g,t.numClippingPlanes).replace(/UNION_CLIPPING_PLANES/g,t.numClippingPlanes-t.numClipIntersection)}var Ec=/^[ \t]*#include +<([\w\d./]+)>/gm;function Dc(e){return e.replace(Ec,kc)}var Oc=new Map;function kc(e,t){let n=Z[t];if(n===void 0){let e=Oc.get(t);if(e!==void 0)n=Z[e],V(`WebGLRenderer: Shader chunk "%s" has been deprecated. Use "%s" instead.`,t,e);else throw Error(`THREE.WebGLProgram: Can not resolve #include <`+t+`>`)}return Dc(n)}var Ac=/#pragma unroll_loop_start\s+for\s*\(\s*int\s+i\s*=\s*(\d+)\s*;\s*i\s*<\s*(\d+)\s*;\s*i\s*\+\+\s*\)\s*{([\s\S]+?)}\s+#pragma unroll_loop_end/g;function jc(e){return e.replace(Ac,Mc)}function Mc(e,t,n,r){let i=``;for(let e=parseInt(t);e<parseInt(n);e++)i+=r.replace(/\[\s*i\s*\]/g,`[ `+e+` ]`).replace(/UNROLLED_LOOP_INDEX/g,e);return i}function Nc(e){let t=`precision ${e.precision} float;
	precision ${e.precision} int;
	precision ${e.precision} sampler2D;
	precision ${e.precision} samplerCube;
	precision ${e.precision} sampler3D;
	precision ${e.precision} sampler2DArray;
	precision ${e.precision} sampler2DShadow;
	precision ${e.precision} samplerCubeShadow;
	precision ${e.precision} sampler2DArrayShadow;
	precision ${e.precision} isampler2D;
	precision ${e.precision} isampler3D;
	precision ${e.precision} isamplerCube;
	precision ${e.precision} isampler2DArray;
	precision ${e.precision} usampler2D;
	precision ${e.precision} usampler3D;
	precision ${e.precision} usamplerCube;
	precision ${e.precision} usampler2DArray;
	`;return e.precision===`highp`?t+=`
#define HIGH_PRECISION`:e.precision===`mediump`?t+=`
#define MEDIUM_PRECISION`:e.precision===`lowp`&&(t+=`
#define LOW_PRECISION`),t}var Pc={1:`SHADOWMAP_TYPE_PCF`,3:`SHADOWMAP_TYPE_VSM`};function Fc(e){return Pc[e.shadowMapType]||`SHADOWMAP_TYPE_BASIC`}var Ic={301:`ENVMAP_TYPE_CUBE`,302:`ENVMAP_TYPE_CUBE`,306:`ENVMAP_TYPE_CUBE_UV`};function Lc(e){return e.envMap===!1?`ENVMAP_TYPE_CUBE`:Ic[e.envMapMode]||`ENVMAP_TYPE_CUBE`}var Rc={302:`ENVMAP_MODE_REFRACTION`};function zc(e){return e.envMap===!1?`ENVMAP_MODE_REFLECTION`:Rc[e.envMapMode]||`ENVMAP_MODE_REFLECTION`}var Bc={0:`ENVMAP_BLENDING_MULTIPLY`,1:`ENVMAP_BLENDING_MIX`,2:`ENVMAP_BLENDING_ADD`};function Vc(e){return e.envMap===!1?`ENVMAP_BLENDING_NONE`:Bc[e.combine]||`ENVMAP_BLENDING_NONE`}function Hc(e){let t=e.envMapCubeUVHeight;if(t===null)return null;let n=Math.log2(t)-2,r=1/t;return{texelWidth:1/(3*Math.max(2**n,112)),texelHeight:r,maxMip:n}}function Uc(e,t,n,r){let i=e.getContext(),a=n.defines,o=n.vertexShader,s=n.fragmentShader,c=Fc(n),l=Lc(n),u=zc(n),d=Vc(n),f=Hc(n),p=bc(n),m=xc(a),h=i.createProgram(),g,_,v=n.glslVersion?`#version `+n.glslVersion+`
`:``;n.isRawShaderMaterial?(g=[`#define SHADER_TYPE `+n.shaderType,`#define SHADER_NAME `+n.shaderName,m].filter(Cc).join(`
`),g.length>0&&(g+=`
`),_=[`#define SHADER_TYPE `+n.shaderType,`#define SHADER_NAME `+n.shaderName,m].filter(Cc).join(`
`),_.length>0&&(_+=`
`)):(g=[Nc(n),`#define SHADER_TYPE `+n.shaderType,`#define SHADER_NAME `+n.shaderName,m,n.extensionClipCullDistance?`#define USE_CLIP_DISTANCE`:``,n.batching?`#define USE_BATCHING`:``,n.batchingColor?`#define USE_BATCHING_COLOR`:``,n.instancing?`#define USE_INSTANCING`:``,n.instancingColor?`#define USE_INSTANCING_COLOR`:``,n.instancingMorph?`#define USE_INSTANCING_MORPH`:``,n.useFog&&n.fog?`#define USE_FOG`:``,n.useFog&&n.fogExp2?`#define FOG_EXP2`:``,n.map?`#define USE_MAP`:``,n.envMap?`#define USE_ENVMAP`:``,n.envMap?`#define `+u:``,n.lightMap?`#define USE_LIGHTMAP`:``,n.aoMap?`#define USE_AOMAP`:``,n.bumpMap?`#define USE_BUMPMAP`:``,n.normalMap?`#define USE_NORMALMAP`:``,n.normalMapObjectSpace?`#define USE_NORMALMAP_OBJECTSPACE`:``,n.normalMapTangentSpace?`#define USE_NORMALMAP_TANGENTSPACE`:``,n.displacementMap?`#define USE_DISPLACEMENTMAP`:``,n.emissiveMap?`#define USE_EMISSIVEMAP`:``,n.anisotropy?`#define USE_ANISOTROPY`:``,n.anisotropyMap?`#define USE_ANISOTROPYMAP`:``,n.clearcoatMap?`#define USE_CLEARCOATMAP`:``,n.clearcoatRoughnessMap?`#define USE_CLEARCOAT_ROUGHNESSMAP`:``,n.clearcoatNormalMap?`#define USE_CLEARCOAT_NORMALMAP`:``,n.iridescenceMap?`#define USE_IRIDESCENCEMAP`:``,n.iridescenceThicknessMap?`#define USE_IRIDESCENCE_THICKNESSMAP`:``,n.specularMap?`#define USE_SPECULARMAP`:``,n.specularColorMap?`#define USE_SPECULAR_COLORMAP`:``,n.specularIntensityMap?`#define USE_SPECULAR_INTENSITYMAP`:``,n.roughnessMap?`#define USE_ROUGHNESSMAP`:``,n.metalnessMap?`#define USE_METALNESSMAP`:``,n.alphaMap?`#define USE_ALPHAMAP`:``,n.alphaHash?`#define USE_ALPHAHASH`:``,n.transmission?`#define USE_TRANSMISSION`:``,n.transmissionMap?`#define USE_TRANSMISSIONMAP`:``,n.thicknessMap?`#define USE_THICKNESSMAP`:``,n.sheenColorMap?`#define USE_SHEEN_COLORMAP`:``,n.sheenRoughnessMap?`#define USE_SHEEN_ROUGHNESSMAP`:``,n.mapUv?`#define MAP_UV `+n.mapUv:``,n.alphaMapUv?`#define ALPHAMAP_UV `+n.alphaMapUv:``,n.lightMapUv?`#define LIGHTMAP_UV `+n.lightMapUv:``,n.aoMapUv?`#define AOMAP_UV `+n.aoMapUv:``,n.emissiveMapUv?`#define EMISSIVEMAP_UV `+n.emissiveMapUv:``,n.bumpMapUv?`#define BUMPMAP_UV `+n.bumpMapUv:``,n.normalMapUv?`#define NORMALMAP_UV `+n.normalMapUv:``,n.displacementMapUv?`#define DISPLACEMENTMAP_UV `+n.displacementMapUv:``,n.metalnessMapUv?`#define METALNESSMAP_UV `+n.metalnessMapUv:``,n.roughnessMapUv?`#define ROUGHNESSMAP_UV `+n.roughnessMapUv:``,n.anisotropyMapUv?`#define ANISOTROPYMAP_UV `+n.anisotropyMapUv:``,n.clearcoatMapUv?`#define CLEARCOATMAP_UV `+n.clearcoatMapUv:``,n.clearcoatNormalMapUv?`#define CLEARCOAT_NORMALMAP_UV `+n.clearcoatNormalMapUv:``,n.clearcoatRoughnessMapUv?`#define CLEARCOAT_ROUGHNESSMAP_UV `+n.clearcoatRoughnessMapUv:``,n.iridescenceMapUv?`#define IRIDESCENCEMAP_UV `+n.iridescenceMapUv:``,n.iridescenceThicknessMapUv?`#define IRIDESCENCE_THICKNESSMAP_UV `+n.iridescenceThicknessMapUv:``,n.sheenColorMapUv?`#define SHEEN_COLORMAP_UV `+n.sheenColorMapUv:``,n.sheenRoughnessMapUv?`#define SHEEN_ROUGHNESSMAP_UV `+n.sheenRoughnessMapUv:``,n.specularMapUv?`#define SPECULARMAP_UV `+n.specularMapUv:``,n.specularColorMapUv?`#define SPECULAR_COLORMAP_UV `+n.specularColorMapUv:``,n.specularIntensityMapUv?`#define SPECULAR_INTENSITYMAP_UV `+n.specularIntensityMapUv:``,n.transmissionMapUv?`#define TRANSMISSIONMAP_UV `+n.transmissionMapUv:``,n.thicknessMapUv?`#define THICKNESSMAP_UV `+n.thicknessMapUv:``,n.vertexTangents&&n.flatShading===!1?`#define USE_TANGENT`:``,n.vertexNormals?`#define HAS_NORMAL`:``,n.vertexColors?`#define USE_COLOR`:``,n.vertexAlphas?`#define USE_COLOR_ALPHA`:``,n.vertexUv1s?`#define USE_UV1`:``,n.vertexUv2s?`#define USE_UV2`:``,n.vertexUv3s?`#define USE_UV3`:``,n.pointsUvs?`#define USE_POINTS_UV`:``,n.flatShading?`#define FLAT_SHADED`:``,n.skinning?`#define USE_SKINNING`:``,n.morphTargets?`#define USE_MORPHTARGETS`:``,n.morphNormals&&n.flatShading===!1?`#define USE_MORPHNORMALS`:``,n.morphColors?`#define USE_MORPHCOLORS`:``,n.morphTargetsCount>0?`#define MORPHTARGETS_TEXTURE_STRIDE `+n.morphTextureStride:``,n.morphTargetsCount>0?`#define MORPHTARGETS_COUNT `+n.morphTargetsCount:``,n.doubleSided?`#define DOUBLE_SIDED`:``,n.flipSided?`#define FLIP_SIDED`:``,n.shadowMapEnabled?`#define USE_SHADOWMAP`:``,n.shadowMapEnabled?`#define `+c:``,n.sizeAttenuation?`#define USE_SIZEATTENUATION`:``,n.numLightProbes>0?`#define USE_LIGHT_PROBES`:``,n.logarithmicDepthBuffer?`#define USE_LOGARITHMIC_DEPTH_BUFFER`:``,n.reversedDepthBuffer?`#define USE_REVERSED_DEPTH_BUFFER`:``,`uniform mat4 modelMatrix;`,`uniform mat4 modelViewMatrix;`,`uniform mat4 projectionMatrix;`,`uniform mat4 viewMatrix;`,`uniform mat3 normalMatrix;`,`uniform vec3 cameraPosition;`,`uniform bool isOrthographic;`,`#ifdef USE_INSTANCING`,`	attribute mat4 instanceMatrix;`,`#endif`,`#ifdef USE_INSTANCING_COLOR`,`	attribute vec3 instanceColor;`,`#endif`,`#ifdef USE_INSTANCING_MORPH`,`	uniform sampler2D morphTexture;`,`#endif`,`attribute vec3 position;`,`attribute vec3 normal;`,`attribute vec2 uv;`,`#ifdef USE_UV1`,`	attribute vec2 uv1;`,`#endif`,`#ifdef USE_UV2`,`	attribute vec2 uv2;`,`#endif`,`#ifdef USE_UV3`,`	attribute vec2 uv3;`,`#endif`,`#ifdef USE_TANGENT`,`	attribute vec4 tangent;`,`#endif`,`#if defined( USE_COLOR_ALPHA )`,`	attribute vec4 color;`,`#elif defined( USE_COLOR )`,`	attribute vec3 color;`,`#endif`,`#ifdef USE_SKINNING`,`	attribute vec4 skinIndex;`,`	attribute vec4 skinWeight;`,`#endif`,`
`].filter(Cc).join(`
`),_=[Nc(n),`#define SHADER_TYPE `+n.shaderType,`#define SHADER_NAME `+n.shaderName,m,n.useFog&&n.fog?`#define USE_FOG`:``,n.useFog&&n.fogExp2?`#define FOG_EXP2`:``,n.alphaToCoverage?`#define ALPHA_TO_COVERAGE`:``,n.map?`#define USE_MAP`:``,n.matcap?`#define USE_MATCAP`:``,n.envMap?`#define USE_ENVMAP`:``,n.envMap?`#define `+l:``,n.envMap?`#define `+u:``,n.envMap?`#define `+d:``,f?`#define CUBEUV_TEXEL_WIDTH `+f.texelWidth:``,f?`#define CUBEUV_TEXEL_HEIGHT `+f.texelHeight:``,f?`#define CUBEUV_MAX_MIP `+f.maxMip+`.0`:``,n.lightMap?`#define USE_LIGHTMAP`:``,n.aoMap?`#define USE_AOMAP`:``,n.bumpMap?`#define USE_BUMPMAP`:``,n.normalMap?`#define USE_NORMALMAP`:``,n.normalMapObjectSpace?`#define USE_NORMALMAP_OBJECTSPACE`:``,n.normalMapTangentSpace?`#define USE_NORMALMAP_TANGENTSPACE`:``,n.packedNormalMap?`#define USE_PACKED_NORMALMAP`:``,n.emissiveMap?`#define USE_EMISSIVEMAP`:``,n.anisotropy?`#define USE_ANISOTROPY`:``,n.anisotropyMap?`#define USE_ANISOTROPYMAP`:``,n.clearcoat?`#define USE_CLEARCOAT`:``,n.clearcoatMap?`#define USE_CLEARCOATMAP`:``,n.clearcoatRoughnessMap?`#define USE_CLEARCOAT_ROUGHNESSMAP`:``,n.clearcoatNormalMap?`#define USE_CLEARCOAT_NORMALMAP`:``,n.dispersion?`#define USE_DISPERSION`:``,n.retroreflection?`#define USE_RETROREFLECTION`:``,n.iridescence?`#define USE_IRIDESCENCE`:``,n.iridescenceMap?`#define USE_IRIDESCENCEMAP`:``,n.iridescenceThicknessMap?`#define USE_IRIDESCENCE_THICKNESSMAP`:``,n.specularMap?`#define USE_SPECULARMAP`:``,n.specularColorMap?`#define USE_SPECULAR_COLORMAP`:``,n.specularIntensityMap?`#define USE_SPECULAR_INTENSITYMAP`:``,n.roughnessMap?`#define USE_ROUGHNESSMAP`:``,n.metalnessMap?`#define USE_METALNESSMAP`:``,n.alphaMap?`#define USE_ALPHAMAP`:``,n.alphaTest?`#define USE_ALPHATEST`:``,n.alphaHash?`#define USE_ALPHAHASH`:``,n.sheen?`#define USE_SHEEN`:``,n.sheenColorMap?`#define USE_SHEEN_COLORMAP`:``,n.sheenRoughnessMap?`#define USE_SHEEN_ROUGHNESSMAP`:``,n.transmission?`#define USE_TRANSMISSION`:``,n.transmissionMap?`#define USE_TRANSMISSIONMAP`:``,n.thicknessMap?`#define USE_THICKNESSMAP`:``,n.vertexTangents&&n.flatShading===!1?`#define USE_TANGENT`:``,n.vertexColors||n.instancingColor?`#define USE_COLOR`:``,n.vertexAlphas||n.batchingColor?`#define USE_COLOR_ALPHA`:``,n.vertexUv1s?`#define USE_UV1`:``,n.vertexUv2s?`#define USE_UV2`:``,n.vertexUv3s?`#define USE_UV3`:``,n.pointsUvs?`#define USE_POINTS_UV`:``,n.gradientMap?`#define USE_GRADIENTMAP`:``,n.flatShading?`#define FLAT_SHADED`:``,n.doubleSided?`#define DOUBLE_SIDED`:``,n.flipSided?`#define FLIP_SIDED`:``,n.shadowMapEnabled?`#define USE_SHADOWMAP`:``,n.shadowMapEnabled?`#define `+c:``,n.premultipliedAlpha?`#define PREMULTIPLIED_ALPHA`:``,n.numLightProbes>0?`#define USE_LIGHT_PROBES`:``,n.numLightProbeGrids>0?`#define USE_LIGHT_PROBES_GRID`:``,n.decodeVideoTexture?`#define DECODE_VIDEO_TEXTURE`:``,n.decodeVideoTextureEmissive?`#define DECODE_VIDEO_TEXTURE_EMISSIVE`:``,n.logarithmicDepthBuffer?`#define USE_LOGARITHMIC_DEPTH_BUFFER`:``,n.reversedDepthBuffer?`#define USE_REVERSED_DEPTH_BUFFER`:``,`uniform mat4 viewMatrix;`,`uniform vec3 cameraPosition;`,`uniform bool isOrthographic;`,n.toneMapping===0?``:`#define TONE_MAPPING`,n.toneMapping===0?``:Z.tonemapping_pars_fragment,n.toneMapping===0?``:_c(`toneMapping`,n.toneMapping),n.dithering?`#define DITHERING`:``,n.opaque?`#define OPAQUE`:``,Z.colorspace_pars_fragment,hc(`linearToOutputTexel`,n.outputColorSpace),yc(),n.useDepthPacking?`#define DEPTH_PACKING `+n.depthPacking:``,`
`].filter(Cc).join(`
`)),o=Dc(o),o=wc(o,n),o=Tc(o,n),s=Dc(s),s=wc(s,n),s=Tc(s,n),o=jc(o),s=jc(s),n.isRawShaderMaterial!==!0&&(v=`#version 300 es
`,g=[p,`#define attribute in`,`#define varying out`,`#define texture2D texture`].join(`
`)+`
`+g,_=[`#define varying in`,n.glslVersion===`300 es`?``:`layout(location = 0) out highp vec4 pc_fragColor;`,n.glslVersion===`300 es`?``:`#define gl_FragColor pc_fragColor`,`#define gl_FragDepthEXT gl_FragDepth`,`#define texture2D texture`,`#define textureCube texture`,`#define texture2DProj textureProj`,`#define texture2DLodEXT textureLod`,`#define texture2DProjLodEXT textureProjLod`,`#define textureCubeLodEXT textureLod`,`#define texture2DGradEXT textureGrad`,`#define texture2DProjGradEXT textureProjGrad`,`#define textureCubeGradEXT textureGrad`].join(`
`)+`
`+_);let y=v+g+o,b=v+_+s,x=cc(i,i.VERTEX_SHADER,y),S=cc(i,i.FRAGMENT_SHADER,b);i.attachShader(h,x),i.attachShader(h,S),n.index0AttributeName===void 0?n.hasPositionAttribute===!0&&i.bindAttribLocation(h,0,`position`):i.bindAttribLocation(h,0,n.index0AttributeName),i.linkProgram(h);function C(t){if(e.debug.checkShaderErrors){let n=i.getProgramInfoLog(h)||``,r=i.getShaderInfoLog(x)||``,a=i.getShaderInfoLog(S)||``,o=n.trim(),s=r.trim(),c=a.trim(),l=!0,u=!0;if(i.getProgramParameter(h,i.LINK_STATUS)===!1){if(l=!1,typeof e.debug.onShaderError==`function`)e.debug.onShaderError(i,h,x,S);else{let e=mc(i,x,`vertex`),n=mc(i,S,`fragment`);H(`WebGLProgram: Shader Error `+i.getError()+` - VALIDATE_STATUS `+i.getProgramParameter(h,i.VALIDATE_STATUS)+`

Material Name: `+t.name+`
Material Type: `+t.type+`

Program Info Log: `+o+`
`+e+`
`+n)}}else o===``?(s===``||c===``)&&(u=!1):V(`WebGLProgram: Program Info Log:`,o);u&&(t.diagnostics={runnable:l,programLog:o,vertexShader:{log:s,prefix:g},fragmentShader:{log:c,prefix:_}})}i.deleteShader(x),i.deleteShader(S),w=new sc(i,h),T=Sc(i,h)}let w;this.getUniforms=function(){return w===void 0&&C(this),w};let T;this.getAttributes=function(){return T===void 0&&C(this),T};let E=n.rendererExtensionParallelShaderCompile===!1;return this.isReady=function(){return E===!1&&(E=i.getProgramParameter(h,lc)),E},this.destroy=function(){r.releaseStatesOfProgram(this),i.deleteProgram(h),this.program=void 0},this.type=n.shaderType,this.name=n.shaderName,this.id=uc++,this.cacheKey=t,this.usedTimes=1,this.program=h,this.vertexShader=x,this.fragmentShader=S,this}var Wc=0,Gc=class{constructor(){this.shaderCache=new Map,this.materialCache=new Map}update(e,t,n){let r=this._getShaderCacheForMaterial(e);return r.has(t)===!1&&(r.add(t),t.usedTimes++),r.has(n)===!1&&(r.add(n),n.usedTimes++),this}remove(e){let t=this.materialCache.get(e);for(let e of t)e.usedTimes--,e.usedTimes===0&&this.shaderCache.delete(e.code);return this.materialCache.delete(e),this}getVertexShaderStage(e){return this._getShaderStage(e.vertexShader)}getFragmentShaderStage(e){return this._getShaderStage(e.fragmentShader)}dispose(){this.shaderCache.clear(),this.materialCache.clear()}_getShaderCacheForMaterial(e){let t=this.materialCache,n=t.get(e);return n===void 0&&(n=new Set,t.set(e,n)),n}_getShaderStage(e){let t=this.shaderCache,n=t.get(e);return n===void 0&&(n=new Kc(e),t.set(e,n)),n}},Kc=class{constructor(e){this.id=Wc++,this.code=e,this.usedTimes=0}};function qc(e){return e===1030||e===37490||e===36285}function Jc(e,t,n,r,i,a){let o=new yn,s=new Gc,c=new Set,l=[],u=new Map,d=r.logarithmicDepthBuffer,f=r.precision,p={MeshDepthMaterial:`depth`,MeshDistanceMaterial:`distance`,MeshNormalMaterial:`normal`,MeshBasicMaterial:`basic`,MeshLambertMaterial:`lambert`,MeshPhongMaterial:`phong`,MeshToonMaterial:`toon`,MeshStandardMaterial:`physical`,MeshPhysicalMaterial:`physical`,MeshMatcapMaterial:`matcap`,LineBasicMaterial:`basic`,LineDashedMaterial:`dashed`,PointsMaterial:`points`,ShadowMaterial:`shadow`,SpriteMaterial:`sprite`};function m(e){return c.add(e),e===0?`uv`:`uv${e}`}function h(i,o,l,u,h,g){let _=u.fog,v=h.geometry,y=i.isMeshStandardMaterial||i.isMeshLambertMaterial||i.isMeshPhongMaterial?u.environment:null,b=i.isMeshStandardMaterial||i.isMeshLambertMaterial&&!i.envMap||i.isMeshPhongMaterial&&!i.envMap,x=t.get(i.envMap||y,b),S=x&&x.mapping===306?x.image.height:null,C=p[i.type];i.precision!==null&&(f=r.getMaxPrecision(i.precision),f!==i.precision&&V(`WebGLProgram.getParameters:`,i.precision,`not supported, using`,f,`instead.`));let w=v.morphAttributes.position||v.morphAttributes.normal||v.morphAttributes.color,T=w===void 0?0:w.length,E=0;v.morphAttributes.position!==void 0&&(E=1),v.morphAttributes.normal!==void 0&&(E=2),v.morphAttributes.color!==void 0&&(E=3);let D,O,k,A;if(C){let e=ho[C];D=e.vertexShader,O=e.fragmentShader}else{D=i.vertexShader,O=i.fragmentShader;let e=s.getVertexShaderStage(i),t=s.getFragmentShaderStage(i);s.update(i,e,t),k=e.id,A=t.id}let j=e.getRenderTarget(),ee=e.state.buffers.depth.getReversed(),M=h.isInstancedMesh===!0,te=h.isBatchedMesh===!0,N=!!i.map,ne=!!i.matcap,re=!!x,ie=!!i.aoMap,ae=!!i.lightMap,oe=!!i.bumpMap&&i.wireframe===!1,P=!!i.normalMap,se=!!i.displacementMap,ce=!!i.emissiveMap,F=!!i.metalnessMap,le=!!i.roughnessMap,ue=i.anisotropy>0,de=i.clearcoat>0,fe=i.dispersion>0,pe=i.retroreflectivity>0,me=i.iridescence>0,he=i.sheen>0,ge=i.transmission>0,_e=ue&&!!i.anisotropyMap,ve=de&&!!i.clearcoatMap,ye=de&&!!i.clearcoatNormalMap,be=de&&!!i.clearcoatRoughnessMap,xe=me&&!!i.iridescenceMap,Se=me&&!!i.iridescenceThicknessMap,Ce=he&&!!i.sheenColorMap,we=he&&!!i.sheenRoughnessMap,Te=!!i.specularMap,Ee=!!i.specularColorMap,De=!!i.specularIntensityMap,Oe=ge&&!!i.transmissionMap,ke=ge&&!!i.thicknessMap,Ae=!!i.gradientMap,je=!!i.alphaMap,Me=i.alphaTest>0,I=!!i.alphaHash,Ne=!!i.extensions,L=0;i.toneMapped&&(j===null||j.isXRRenderTarget===!0)&&(L=e.toneMapping);let Pe={shaderID:C,shaderType:i.type,shaderName:i.name,vertexShader:D,fragmentShader:O,defines:i.defines,customVertexShaderID:k,customFragmentShaderID:A,isRawShaderMaterial:i.isRawShaderMaterial===!0,glslVersion:i.glslVersion,precision:f,batching:te,batchingColor:te&&h._colorsTexture!==null,instancing:M,instancingColor:M&&h.instanceColor!==null,instancingMorph:M&&h.morphTexture!==null,outputColorSpace:j===null?e.outputColorSpace:j.isXRRenderTarget===!0?j.texture.colorSpace:q.workingColorSpace,alphaToCoverage:!!i.alphaToCoverage,map:N,matcap:ne,envMap:re,envMapMode:re&&x.mapping,envMapCubeUVHeight:S,aoMap:ie,lightMap:ae,bumpMap:oe,normalMap:P,displacementMap:se,emissiveMap:ce,normalMapObjectSpace:P&&i.normalMapType===1,normalMapTangentSpace:P&&i.normalMapType===0,packedNormalMap:P&&i.normalMapType===0&&qc(i.normalMap.format),metalnessMap:F,roughnessMap:le,anisotropy:ue,anisotropyMap:_e,clearcoat:de,clearcoatMap:ve,clearcoatNormalMap:ye,clearcoatRoughnessMap:be,dispersion:fe,retroreflection:pe,iridescence:me,iridescenceMap:xe,iridescenceThicknessMap:Se,sheen:he,sheenColorMap:Ce,sheenRoughnessMap:we,specularMap:Te,specularColorMap:Ee,specularIntensityMap:De,transmission:ge,transmissionMap:Oe,thicknessMap:ke,gradientMap:Ae,opaque:i.transparent===!1&&i.blending===1&&i.alphaToCoverage===!1,alphaMap:je,alphaTest:Me,alphaHash:I,combine:i.combine,mapUv:N&&m(i.map.channel),aoMapUv:ie&&m(i.aoMap.channel),lightMapUv:ae&&m(i.lightMap.channel),bumpMapUv:oe&&m(i.bumpMap.channel),normalMapUv:P&&m(i.normalMap.channel),displacementMapUv:se&&m(i.displacementMap.channel),emissiveMapUv:ce&&m(i.emissiveMap.channel),metalnessMapUv:F&&m(i.metalnessMap.channel),roughnessMapUv:le&&m(i.roughnessMap.channel),anisotropyMapUv:_e&&m(i.anisotropyMap.channel),clearcoatMapUv:ve&&m(i.clearcoatMap.channel),clearcoatNormalMapUv:ye&&m(i.clearcoatNormalMap.channel),clearcoatRoughnessMapUv:be&&m(i.clearcoatRoughnessMap.channel),iridescenceMapUv:xe&&m(i.iridescenceMap.channel),iridescenceThicknessMapUv:Se&&m(i.iridescenceThicknessMap.channel),sheenColorMapUv:Ce&&m(i.sheenColorMap.channel),sheenRoughnessMapUv:we&&m(i.sheenRoughnessMap.channel),specularMapUv:Te&&m(i.specularMap.channel),specularColorMapUv:Ee&&m(i.specularColorMap.channel),specularIntensityMapUv:De&&m(i.specularIntensityMap.channel),transmissionMapUv:Oe&&m(i.transmissionMap.channel),thicknessMapUv:ke&&m(i.thicknessMap.channel),alphaMapUv:je&&m(i.alphaMap.channel),vertexTangents:!!v.attributes.tangent&&(P||ue),vertexNormals:!!v.attributes.normal,vertexColors:i.vertexColors,vertexAlphas:i.vertexColors===!0&&!!v.attributes.color&&v.attributes.color.itemSize===4,pointsUvs:h.isPoints===!0&&!!v.attributes.uv&&(N||je),fog:!!_,useFog:i.fog===!0,fogExp2:!!_&&_.isFogExp2,flatShading:i.wireframe===!1&&(i.flatShading===!0||v.attributes.normal===void 0&&P===!1&&(i.isMeshLambertMaterial||i.isMeshPhongMaterial||i.isMeshStandardMaterial||i.isMeshPhysicalMaterial)),sizeAttenuation:i.sizeAttenuation===!0,logarithmicDepthBuffer:d,reversedDepthBuffer:ee,skinning:h.isSkinnedMesh===!0,hasPositionAttribute:v.attributes.position!==void 0,morphTargets:v.morphAttributes.position!==void 0,morphNormals:v.morphAttributes.normal!==void 0,morphColors:v.morphAttributes.color!==void 0,morphTargetsCount:T,morphTextureStride:E,numSunLights:o.sun.length,numDirLights:o.directional.length,numPointLights:o.point.length,numSpotLights:o.spot.length,numSpotLightMaps:o.spotLightMap.length,numRectAreaLights:o.rectArea.length,numHemiLights:o.hemi.length,numSunLightShadows:o.sunShadowMap.length,numDirLightShadows:o.directionalShadowMap.length,numPointLightShadows:o.pointShadowMap.length,numSpotLightShadows:o.spotShadowMap.length,numSpotLightShadowsWithMaps:o.numSpotLightShadowsWithMaps,numLightProbes:o.numLightProbes,numLightProbeGrids:g.length,numClippingPlanes:a.numPlanes,numClipIntersection:a.numIntersection,dithering:i.dithering,shadowMapEnabled:e.shadowMap.enabled&&l.length>0,shadowMapType:e.shadowMap.type,toneMapping:L,decodeVideoTexture:N&&i.map.isVideoTexture===!0&&q.getTransfer(i.map.colorSpace)===`srgb`,decodeVideoTextureEmissive:ce&&i.emissiveMap.isVideoTexture===!0&&q.getTransfer(i.emissiveMap.colorSpace)===`srgb`,premultipliedAlpha:i.premultipliedAlpha,doubleSided:i.side===2,flipSided:i.side===1,useDepthPacking:i.depthPacking>=0,depthPacking:i.depthPacking||0,index0AttributeName:i.index0AttributeName,extensionClipCullDistance:Ne&&i.extensions.clipCullDistance===!0&&n.has(`WEBGL_clip_cull_distance`),extensionMultiDraw:(Ne&&i.extensions.multiDraw===!0||te)&&n.has(`WEBGL_multi_draw`),rendererExtensionParallelShaderCompile:n.has(`KHR_parallel_shader_compile`),customProgramCacheKey:i.customProgramCacheKey()};return Pe.vertexUv1s=c.has(1),Pe.vertexUv2s=c.has(2),Pe.vertexUv3s=c.has(3),c.clear(),Pe}function g(t){let n=[];if(t.shaderID?n.push(t.shaderID):(n.push(t.customVertexShaderID),n.push(t.customFragmentShaderID)),t.defines!==void 0)for(let e in t.defines)n.push(e),n.push(t.defines[e]);return t.isRawShaderMaterial===!1&&(_(n,t),v(n,t),n.push(e.outputColorSpace)),n.push(t.customProgramCacheKey),n.join()}function _(e,t){e.push(t.precision),e.push(t.outputColorSpace),e.push(t.envMapMode),e.push(t.envMapCubeUVHeight),e.push(t.mapUv),e.push(t.alphaMapUv),e.push(t.lightMapUv),e.push(t.aoMapUv),e.push(t.bumpMapUv),e.push(t.normalMapUv),e.push(t.displacementMapUv),e.push(t.emissiveMapUv),e.push(t.metalnessMapUv),e.push(t.roughnessMapUv),e.push(t.anisotropyMapUv),e.push(t.clearcoatMapUv),e.push(t.clearcoatNormalMapUv),e.push(t.clearcoatRoughnessMapUv),e.push(t.iridescenceMapUv),e.push(t.iridescenceThicknessMapUv),e.push(t.sheenColorMapUv),e.push(t.sheenRoughnessMapUv),e.push(t.specularMapUv),e.push(t.specularColorMapUv),e.push(t.specularIntensityMapUv),e.push(t.transmissionMapUv),e.push(t.thicknessMapUv),e.push(t.combine),e.push(t.fogExp2),e.push(t.sizeAttenuation),e.push(t.morphTargetsCount),e.push(t.morphAttributeCount),e.push(t.numSunLights),e.push(t.numDirLights),e.push(t.numPointLights),e.push(t.numSpotLights),e.push(t.numSpotLightMaps),e.push(t.numHemiLights),e.push(t.numRectAreaLights),e.push(t.numSunLightShadows),e.push(t.numDirLightShadows),e.push(t.numPointLightShadows),e.push(t.numSpotLightShadows),e.push(t.numSpotLightShadowsWithMaps),e.push(t.numLightProbes),e.push(t.shadowMapType),e.push(t.toneMapping),e.push(t.numClippingPlanes),e.push(t.numClipIntersection),e.push(t.depthPacking)}function v(e,t){o.disableAll(),t.instancing&&o.enable(0),t.instancingColor&&o.enable(1),t.instancingMorph&&o.enable(2),t.matcap&&o.enable(3),t.envMap&&o.enable(4),t.normalMapObjectSpace&&o.enable(5),t.normalMapTangentSpace&&o.enable(6),t.clearcoat&&o.enable(7),t.iridescence&&o.enable(8),t.alphaTest&&o.enable(9),t.vertexColors&&o.enable(10),t.vertexAlphas&&o.enable(11),t.vertexUv1s&&o.enable(12),t.vertexUv2s&&o.enable(13),t.vertexUv3s&&o.enable(14),t.vertexTangents&&o.enable(15),t.anisotropy&&o.enable(16),t.alphaHash&&o.enable(17),t.batching&&o.enable(18),t.dispersion&&o.enable(19),t.retroreflection&&o.enable(24),t.batchingColor&&o.enable(20),t.gradientMap&&o.enable(21),t.packedNormalMap&&o.enable(22),t.vertexNormals&&o.enable(23),e.push(o.mask),o.disableAll(),t.fog&&o.enable(0),t.useFog&&o.enable(1),t.flatShading&&o.enable(2),t.logarithmicDepthBuffer&&o.enable(3),t.reversedDepthBuffer&&o.enable(4),t.skinning&&o.enable(5),t.morphTargets&&o.enable(6),t.morphNormals&&o.enable(7),t.morphColors&&o.enable(8),t.premultipliedAlpha&&o.enable(9),t.shadowMapEnabled&&o.enable(10),t.doubleSided&&o.enable(11),t.flipSided&&o.enable(12),t.useDepthPacking&&o.enable(13),t.dithering&&o.enable(14),t.transmission&&o.enable(15),t.sheen&&o.enable(16),t.opaque&&o.enable(17),t.pointsUvs&&o.enable(18),t.decodeVideoTexture&&o.enable(19),t.decodeVideoTextureEmissive&&o.enable(20),t.alphaToCoverage&&o.enable(21),t.numLightProbeGrids>0&&o.enable(22),t.hasPositionAttribute&&o.enable(23),e.push(o.mask)}function y(e){let t=p[e.type],n;if(t){let e=ho[t];n=la.clone(e.uniforms)}else n=e.uniforms;return n}function b(t,n){let r=u.get(n);return r===void 0?(r=new Uc(e,n,t,i),l.push(r),u.set(n,r)):++r.usedTimes,r}function x(e){if(--e.usedTimes===0){let t=l.indexOf(e);l[t]=l[l.length-1],l.pop(),u.delete(e.cacheKey),e.destroy()}}function S(e){s.remove(e)}function C(){s.dispose()}return{getParameters:h,getProgramCacheKey:g,getUniforms:y,acquireProgram:b,releaseProgram:x,releaseShaderCache:S,programs:l,dispose:C}}function Yc(){let e=new WeakMap;function t(t){return e.has(t)}function n(t){let n=e.get(t);return n===void 0&&(n={},e.set(t,n)),n}function r(t){e.delete(t)}function i(t,n,r){e.get(t)[n]=r}function a(){e=new WeakMap}return{has:t,get:n,remove:r,update:i,dispose:a}}function Xc(e,t){return e.groupOrder===t.groupOrder?e.renderOrder===t.renderOrder?e.material.id===t.material.id?e.materialVariant===t.materialVariant?e.z===t.z?e.id-t.id:e.z-t.z:e.materialVariant-t.materialVariant:e.material.id-t.material.id:e.renderOrder-t.renderOrder:e.groupOrder-t.groupOrder}function Zc(e,t){return e.groupOrder===t.groupOrder?e.renderOrder===t.renderOrder?e.z===t.z?e.id-t.id:t.z-e.z:e.renderOrder-t.renderOrder:e.groupOrder-t.groupOrder}function Qc(){let e=[],t=0,n=[],r=[],i=[];function a(){t=0,n.length=0,r.length=0,i.length=0}function o(e){let t=0;return e.isInstancedMesh&&(t+=2),e.isSkinnedMesh&&(t+=1),t}function s(n,r,i,a,s,c){let l=e[t];return l===void 0?(l={id:n.id,object:n,geometry:r,material:i,materialVariant:o(n),groupOrder:a,renderOrder:n.renderOrder,z:s,group:c},e[t]=l):(l.id=n.id,l.object=n,l.geometry=r,l.material=i,l.materialVariant=o(n),l.groupOrder=a,l.renderOrder=n.renderOrder,l.z=s,l.group=c),t++,l}function c(e,t,a,o,c,l,u){u.reversedDepth===!0&&(c=-c);let d=s(e,t,a,o,c,l);a.transmission>0?r.push(d):a.transparent===!0?i.push(d):n.push(d)}function l(e,t,a,o,c,l){let u=s(e,t,a,o,c,l);a.transmission>0?r.unshift(u):a.transparent===!0?i.unshift(u):n.unshift(u)}function u(e,t){n.length>1&&n.sort(e||Xc),r.length>1&&r.sort(t||Zc),i.length>1&&i.sort(t||Zc)}function d(){for(let n=t,r=e.length;n<r;n++){let t=e[n];if(t.id===null)break;t.id=null,t.object=null,t.geometry=null,t.material=null,t.group=null}}return{opaque:n,transmissive:r,transparent:i,init:a,push:c,unshift:l,finish:d,sort:u}}function $c(){let e=new WeakMap;function t(t,n){let r=e.get(t),i;return r===void 0?(i=new Qc,e.set(t,[i])):n>=r.length?(i=new Qc,r.push(i)):i=r[n],i}function n(){e=new WeakMap}return{get:t,dispose:n}}function el(){let e={};return{get:function(t){if(e[t.id]!==void 0)return e[t.id];let n;switch(t.type){case`SunLight`:case`DirectionalLight`:n={direction:new G,color:new Y};break;case`SpotLight`:n={position:new G,direction:new G,color:new Y,distance:0,coneCos:0,penumbraCos:0,decay:0};break;case`PointLight`:n={position:new G,color:new Y,distance:0,decay:0};break;case`HemisphereLight`:n={direction:new G,skyColor:new Y,groundColor:new Y};break;case`RectAreaLight`:n={color:new Y,position:new G,halfWidth:new G,halfHeight:new G}}return e[t.id]=n,n}}}function tl(){let e={};return{get:function(t){if(e[t.id]!==void 0)return e[t.id];let n;switch(t.type){case`SunLight`:case`DirectionalLight`:n={shadowIntensity:1,shadowBias:0,shadowNormalBias:0,shadowRadius:1,shadowMapSize:new W};break;case`SpotLight`:n={shadowIntensity:1,shadowBias:0,shadowNormalBias:0,shadowRadius:1,shadowMapSize:new W};break;case`PointLight`:n={shadowIntensity:1,shadowBias:0,shadowNormalBias:0,shadowRadius:1,shadowMapSize:new W,shadowCameraNear:1,shadowCameraFar:1e3}}return e[t.id]=n,n}}}var nl=0;function rl(e,t){return(t.castShadow?2:0)-(e.castShadow?2:0)+ +!!t.map-!!e.map}function il(e){let t=new el,n=tl(),r={version:0,hash:{sunLength:-1,directionalLength:-1,pointLength:-1,spotLength:-1,rectAreaLength:-1,hemiLength:-1,numSunShadows:-1,numDirectionalShadows:-1,numPointShadows:-1,numSpotShadows:-1,numSpotMaps:-1,numLightProbes:-1},ambient:[0,0,0],probe:[],sun:[],sunShadow:[],sunShadowMap:[],sunShadowMatrix:[],sunShadowCascade:[],directional:[],directionalShadow:[],directionalShadowMap:[],directionalShadowMatrix:[],spot:[],spotLightMap:[],spotShadow:[],spotShadowMap:[],spotLightMatrix:[],rectArea:[],rectAreaLTC1:null,rectAreaLTC2:null,point:[],pointShadow:[],pointShadowMap:[],pointShadowMatrix:[],hemi:[],numSpotLightShadowsWithMaps:0,numLightProbes:0};for(let e=0;e<9;e++)r.probe.push(new G);let i=new G,a=new J,o=new J;function s(i){let a=0,o=0,s=0;for(let e=0;e<9;e++)r.probe[e].set(0,0,0);let c=0,l=0,u=0,d=0,f=0,p=0,m=0,h=0,g=0,_=0,v=0,y=0,b=0,x=0;i.sort(rl);for(let e=0,S=i.length;e<S;e++){let S=i[e],C=S.color,w=S.intensity,T=S.distance,E=null;if(S.shadow&&S.shadow.map&&(E=S.shadow.map.texture.format===1030?S.shadow.map.texture:S.shadow.map.depthTexture||S.shadow.map.texture),S.isAmbientLight)a+=C.r*w,o+=C.g*w,s+=C.b*w;else if(S.isLightProbe){for(let e=0;e<9;e++)r.probe[e].addScaledVector(S.sh.coefficients[e],w);x++}else if(S.isSunLight){let e=t.get(S);if(e.color.copy(S.color).multiplyScalar(S.intensity),S.castShadow){let e=S.shadow,t=n.get(S);t.shadowIntensity=e.intensity,t.shadowBias=e.bias,t.shadowNormalBias=e.normalBias,t.shadowRadius=e.radius,t.shadowMapSize.copy(e.mapSize).multiply(e.getFrameExtents()),r.sunShadow[l]=t,r.sunShadowMap[l]=E;let i=e.getViewportCount();for(let t=0;t<i;t++)r.sunShadowMatrix[u+t]=e.getMatrix(t),r.sunShadowCascade[u+t]=e._cascadeData[t];u+=i,l++}r.sun[c]=e,c++}else if(S.isDirectionalLight){let e=t.get(S);if(e.color.copy(S.color).multiplyScalar(S.intensity),S.castShadow){let e=S.shadow,t=n.get(S);t.shadowIntensity=e.intensity,t.shadowBias=e.bias,t.shadowNormalBias=e.normalBias,t.shadowRadius=e.radius,t.shadowMapSize=e.mapSize,r.directionalShadow[d]=t,r.directionalShadowMap[d]=E,r.directionalShadowMatrix[d]=S.shadow.matrix,g++}r.directional[d]=e,d++}else if(S.isSpotLight){let e=t.get(S);e.position.setFromMatrixPosition(S.matrixWorld),e.color.copy(C).multiplyScalar(w),e.distance=T,e.coneCos=Math.cos(S.angle),e.penumbraCos=Math.cos(S.angle*(1-S.penumbra)),e.decay=S.decay,r.spot[p]=e;let i=S.shadow;if(S.map&&(r.spotLightMap[y]=S.map,y++,i.updateMatrices(S),S.castShadow&&b++),r.spotLightMatrix[p]=i.matrix,S.castShadow){let e=n.get(S);e.shadowIntensity=i.intensity,e.shadowBias=i.bias,e.shadowNormalBias=i.normalBias,e.shadowRadius=i.radius,e.shadowMapSize=i.mapSize,r.spotShadow[p]=e,r.spotShadowMap[p]=E,v++}p++}else if(S.isRectAreaLight){let e=t.get(S);e.color.copy(C).multiplyScalar(w),e.halfWidth.set(S.width*.5,0,0),e.halfHeight.set(0,S.height*.5,0),r.rectArea[m]=e,m++}else if(S.isPointLight){let e=t.get(S);if(e.color.copy(S.color).multiplyScalar(S.intensity),e.distance=S.distance,e.decay=S.decay,S.castShadow){let e=S.shadow,t=n.get(S);t.shadowIntensity=e.intensity,t.shadowBias=e.bias,t.shadowNormalBias=e.normalBias,t.shadowRadius=e.radius,t.shadowMapSize=e.mapSize,t.shadowCameraNear=e.camera.near,t.shadowCameraFar=e.camera.far,r.pointShadow[f]=t,r.pointShadowMap[f]=E,r.pointShadowMatrix[f]=S.shadow.matrix,_++}r.point[f]=e,f++}else if(S.isHemisphereLight){let e=t.get(S);e.skyColor.copy(S.color).multiplyScalar(w),e.groundColor.copy(S.groundColor).multiplyScalar(w),r.hemi[h]=e,h++}}m>0&&(e.has(`OES_texture_float_linear`)===!0?(r.rectAreaLTC1=Q.LTC_FLOAT_1,r.rectAreaLTC2=Q.LTC_FLOAT_2):(r.rectAreaLTC1=Q.LTC_HALF_1,r.rectAreaLTC2=Q.LTC_HALF_2)),r.ambient[0]=a,r.ambient[1]=o,r.ambient[2]=s;let S=r.hash;(S.sunLength!==c||S.directionalLength!==d||S.pointLength!==f||S.spotLength!==p||S.rectAreaLength!==m||S.hemiLength!==h||S.numSunShadows!==l||S.numDirectionalShadows!==g||S.numPointShadows!==_||S.numSpotShadows!==v||S.numSpotMaps!==y||S.numLightProbes!==x)&&(r.sun.length=c,r.directional.length=d,r.spot.length=p,r.rectArea.length=m,r.point.length=f,r.hemi.length=h,r.sunShadow.length=l,r.sunShadowMap.length=l,r.sunShadowMatrix.length=u,r.sunShadowCascade.length=u,r.directionalShadow.length=g,r.directionalShadowMap.length=g,r.directionalShadowMatrix.length=g,r.pointShadow.length=_,r.pointShadowMap.length=_,r.pointShadowMatrix.length=_,r.spotShadow.length=v,r.spotShadowMap.length=v,r.spotLightMatrix.length=v+y-b,r.spotLightMap.length=y,r.numSpotLightShadowsWithMaps=b,r.numLightProbes=x,S.sunLength=c,S.directionalLength=d,S.pointLength=f,S.spotLength=p,S.rectAreaLength=m,S.hemiLength=h,S.numSunShadows=l,S.numDirectionalShadows=g,S.numPointShadows=_,S.numSpotShadows=v,S.numSpotMaps=y,S.numLightProbes=x,r.version=nl++)}function c(e,t){let n=0,s=0,c=0,l=0,u=0,d=0,f=t.matrixWorldInverse;for(let t=0,p=e.length;t<p;t++){let p=e[t];if(p.isSunLight){let e=r.sun[n];e.direction.setFromMatrixPosition(p.matrixWorld),e.direction.transformDirection(f),n++}else if(p.isDirectionalLight){let e=r.directional[s];e.direction.setFromMatrixPosition(p.matrixWorld),i.setFromMatrixPosition(p.target.matrixWorld),e.direction.sub(i),e.direction.transformDirection(f),s++}else if(p.isSpotLight){let e=r.spot[l];e.position.setFromMatrixPosition(p.matrixWorld),e.position.applyMatrix4(f),e.direction.setFromMatrixPosition(p.matrixWorld),i.setFromMatrixPosition(p.target.matrixWorld),e.direction.sub(i),e.direction.transformDirection(f),l++}else if(p.isRectAreaLight){let e=r.rectArea[u];e.position.setFromMatrixPosition(p.matrixWorld),e.position.applyMatrix4(f),o.identity(),a.copy(p.matrixWorld),a.premultiply(f),o.extractRotation(a),e.halfWidth.set(p.width*.5,0,0),e.halfHeight.set(0,p.height*.5,0),e.halfWidth.applyMatrix4(o),e.halfHeight.applyMatrix4(o),u++}else if(p.isPointLight){let e=r.point[c];e.position.setFromMatrixPosition(p.matrixWorld),e.position.applyMatrix4(f),c++}else if(p.isHemisphereLight){let e=r.hemi[d];e.direction.setFromMatrixPosition(p.matrixWorld),e.direction.transformDirection(f),d++}}}return{setup:s,setupView:c,state:r}}function al(e){let t=new il(e),n=[],r=[],i=[];function a(e){d.camera=e,n.length=0,r.length=0,i.length=0}function o(e){n.push(e)}function s(e){r.push(e)}function c(e){i.push(e)}function l(){t.setup(n)}function u(e){t.setupView(n,e)}let d={lightsArray:n,shadowsArray:r,lightProbeGridArray:i,camera:null,lights:t,transmissionRenderTarget:{},textureUnits:0};return{init:a,state:d,setupLights:l,setupLightsView:u,pushLight:o,pushShadow:s,pushLightProbeGrid:c}}function ol(e){let t=new WeakMap;function n(n,r=0){let i=t.get(n),a;return i===void 0?(a=new al(e),t.set(n,[a])):r>=i.length?(a=new al(e),i.push(a)):a=i[r],a}function r(){t=new WeakMap}return{get:n,dispose:r}}var sl=`void main() {
	gl_Position = vec4( position, 1.0 );
}`,cl=`uniform sampler2D shadow_pass;
uniform vec2 resolution;
uniform float radius;
void main() {
	const float samples = float( VSM_SAMPLES );
	float mean = 0.0;
	float squared_mean = 0.0;
	float uvStride = samples <= 1.0 ? 0.0 : 2.0 / ( samples - 1.0 );
	float uvStart = samples <= 1.0 ? 0.0 : - 1.0;
	for ( float i = 0.0; i < samples; i ++ ) {
		float uvOffset = uvStart + i * uvStride;
		#ifdef HORIZONTAL_PASS
			vec2 distribution = texture2D( shadow_pass, ( gl_FragCoord.xy + vec2( uvOffset, 0.0 ) * radius ) / resolution ).rg;
			mean += distribution.x;
			squared_mean += distribution.y * distribution.y + distribution.x * distribution.x;
		#else
			float depth = texture2D( shadow_pass, ( gl_FragCoord.xy + vec2( 0.0, uvOffset ) * radius ) / resolution ).r;
			mean += depth;
			squared_mean += depth * depth;
		#endif
	}
	mean = mean / samples;
	squared_mean = squared_mean / samples;
	float std_dev = sqrt( max( 0.0, squared_mean - mean * mean ) );
	gl_FragColor = vec4( mean, std_dev, 0.0, 1.0 );
}`,ll=[new G(1,0,0),new G(-1,0,0),new G(0,1,0),new G(0,-1,0),new G(0,0,1),new G(0,0,-1)],ul=[new G(0,-1,0),new G(0,-1,0),new G(0,0,1),new G(0,0,-1),new G(0,-1,0),new G(0,-1,0)],dl=new J,fl=new G,pl=new G;function ml(e,t,n){let r=new wi,i=new W,a=new W,o=new rn,s=new ma,c=new ha,l={},u=n.maxTextureSize,d={0:1,1:0,2:2},f=new fa({defines:{VSM_SAMPLES:8},uniforms:{shadow_pass:{value:null},resolution:{value:new W},radius:{value:4}},vertexShader:sl,fragmentShader:cl}),p=f.clone();p.defines.HORIZONTAL_PASS=1;let m=new Rr;m.setAttribute(`position`,new X(new Float32Array([-1,-1,.5,3,-1,.5,-1,3,.5]),3));let h=new ci(m,f),g=this;this.enabled=!1,this.autoUpdate=!0,this.needsUpdate=!1,this.type=1;let _=this.type;this.render=function(t,n,s){if(g.enabled===!1||g.autoUpdate===!1&&g.needsUpdate===!1||t.length===0)return;this.type===2&&(V(`WebGLShadowMap: PCFSoftShadowMap has been removed. Using PCFShadowMap instead.`),this.type=1);let c=e.getRenderTarget(),l=e.getActiveCubeFace(),d=e.getActiveMipmapLevel(),f=e.state;f.setBlending(0),f.buffers.depth.getReversed()===!0?f.buffers.color.setClear(0,0,0,0):f.buffers.color.setClear(1,1,1,1),f.buffers.depth.setTest(!0),f.setScissorTest(!1);let p=_!==this.type;p&&n.traverse(function(e){e.material&&(Array.isArray(e.material)?e.material.forEach(e=>e.needsUpdate=!0):e.material.needsUpdate=!0)});for(let c=0,l=t.length;c<l;c++){let l=t[c],d=l.shadow;if(d===void 0){V(`WebGLShadowMap:`,l,`has no shadow.`);continue}if(d.autoUpdate===!1&&d.needsUpdate===!1)continue;i.copy(d.mapSize);let m=d.getFrameExtents();i.multiply(m),a.copy(d.mapSize),(i.x>u||i.y>u)&&(i.x>u&&(a.x=Math.floor(u/m.x),i.x=a.x*m.x,d.mapSize.x=a.x),i.y>u&&(a.y=Math.floor(u/m.y),i.y=a.y*m.y,d.mapSize.y=a.y));let h=e.state.buffers.depth.getReversed();if(d.camera._reversedDepth=h,d.map===null||p===!0){if(d.map!==null&&(d.map.depthTexture!==null&&(d.map.depthTexture.dispose(),d.map.depthTexture=null),d.map.dispose()),this.type===3){if(l.isPointLight){V(`WebGLShadowMap: VSM shadow maps are not supported for PointLights. Use PCF or BasicShadowMap instead.`);continue}d.map=new on(i.x,i.y,{format:F,type:j,minFilter:x,magFilter:x,generateMipmaps:!1}),d.map.texture.name=l.name+`.shadowMap`,d.map.depthTexture=new qi(i.x,i.y,A),d.map.depthTexture.name=l.name+`.shadowMapDepth`,d.map.depthTexture.format=oe,d.map.depthTexture.compareFunction=null,d.map.depthTexture.minFilter=v,d.map.depthTexture.magFilter=v}else l.isPointLight?(d.map=new Go(i.x),d.map.depthTexture=new Ji(i.x,k)):(d.map=new on(i.x,i.y),d.map.depthTexture=new qi(i.x,i.y,k)),d.map.depthTexture.name=l.name+`.shadowMap`,d.map.depthTexture.format=oe,this.type===1?(d.map.depthTexture.compareFunction=h?518:515,d.map.depthTexture.minFilter=x,d.map.depthTexture.magFilter=x):(d.map.depthTexture.compareFunction=null,d.map.depthTexture.minFilter=v,d.map.depthTexture.magFilter=v);d.camera.updateProjectionMatrix()}d.map.isWebGLCubeRenderTarget!==!0&&(d.map.width!==i.x||d.map.height!==i.y)&&d.map.setSize(i.x,i.y);let g=d.map.isWebGLCubeRenderTarget?6:d.getViewportCount();l.isPointLight!==!0&&d.updateMatrices(l,s);for(let t=0;t<g;t++){let i=d.getCamera(t);if(l.isPointLight){let e=d.camera,n=d.matrix,r=l.distance||e.far;r!==e.far&&(e.far=r,e.updateProjectionMatrix()),fl.setFromMatrixPosition(l.matrixWorld),e.position.copy(fl),pl.copy(e.position),pl.add(ll[t]),e.up.copy(ul[t]),e.lookAt(pl),e.updateMatrixWorld(),n.makeTranslation(-fl.x,-fl.y,-fl.z),dl.multiplyMatrices(e.projectionMatrix,e.matrixWorldInverse),d._frustum.setFromProjectionMatrix(dl,e.coordinateSystem,e.reversedDepth)}if(d.map.isWebGLCubeRenderTarget)e.setRenderTarget(d.map,t),e.clear();else{t===0&&(e.setRenderTarget(d.map),e.clear());let n=d.getViewport(t);o.set(a.x*n.x,a.y*n.y,a.x*n.z,a.y*n.w),f.viewport(o)}r=d.getFrustum(t),S(n,s,i,l,this.type)}d.isPointLightShadow!==!0&&this.type===3&&y(d,s),d.needsUpdate=!1}_=this.type,g.needsUpdate=!1,e.setRenderTarget(c,l,d)};function y(n,r){let a=t.update(h);f.defines.VSM_SAMPLES!==n.blurSamples&&(f.defines.VSM_SAMPLES=n.blurSamples,p.defines.VSM_SAMPLES=n.blurSamples,f.needsUpdate=!0,p.needsUpdate=!0),n.mapPass===null?n.mapPass=new on(i.x,i.y,{format:F,type:j}):(n.mapPass.width!==n.map.width||n.mapPass.height!==n.map.height)&&n.mapPass.setSize(n.map.width,n.map.height),f.uniforms.shadow_pass.value=n.map.depthTexture,f.uniforms.resolution.value.set(n.map.width,n.map.height),f.uniforms.radius.value=n.radius,e.setRenderTarget(n.mapPass),e.clear(),e.renderBufferDirect(r,null,a,f,h,null),p.uniforms.shadow_pass.value=n.mapPass.texture,p.uniforms.resolution.value.set(n.map.width,n.map.height),p.uniforms.radius.value=n.radius,e.setRenderTarget(n.map),e.clear(),e.renderBufferDirect(r,null,a,p,h,null)}function b(t,n,r,i){let a=null,o=r.isPointLight===!0?t.customDistanceMaterial:t.customDepthMaterial;if(o!==void 0)a=o;else if(a=r.isPointLight===!0?c:s,e.localClippingEnabled&&n.clipShadows===!0&&Array.isArray(n.clippingPlanes)&&n.clippingPlanes.length!==0||n.displacementMap&&n.displacementScale!==0||n.alphaMap&&n.alphaTest>0||n.map&&n.alphaTest>0||n.alphaToCoverage===!0){let e=a.uuid,t=n.uuid,r=l[e];r===void 0&&(r={},l[e]=r);let i=r[t];i===void 0&&(i=a.clone(),r[t]=i,n.addEventListener(`dispose`,C)),a=i}if(a.visible=n.visible,a.wireframe=n.wireframe,i===3?a.side=n.shadowSide===null?n.side:n.shadowSide:a.side=n.shadowSide===null?d[n.side]:n.shadowSide,a.alphaMap=n.alphaMap,a.alphaTest=n.alphaToCoverage===!0?.5:n.alphaTest,a.map=n.map,a.clipShadows=n.clipShadows,a.clippingPlanes=n.clippingPlanes,a.clipIntersection=n.clipIntersection,a.displacementMap=n.displacementMap,a.displacementScale=n.displacementScale,a.displacementBias=n.displacementBias,a.wireframeLinewidth=n.wireframeLinewidth,a.linewidth=n.linewidth,r.isPointLight===!0&&a.isMeshDistanceMaterial===!0){let t=e.properties.get(a);t.light=r}return a}function S(n,i,a,o,s){if(n.visible===!1)return;if(n.layers.test(i.layers)&&(n.isMesh||n.isLine||n.isPoints)&&(n.castShadow||n.receiveShadow&&s===3)&&(!n.frustumCulled||n.intersectsFrustum(r))){n.modelViewMatrix.multiplyMatrices(a.matrixWorldInverse,n.matrixWorld);let r=t.update(n),c=n.material;if(Array.isArray(c)){let t=r.groups;for(let l=0,u=t.length;l<u;l++){let u=t[l],d=c[u.materialIndex];if(d&&d.visible){let t=b(n,d,o,s);n.onBeforeShadow(e,n,i,a,r,t,u),e.renderBufferDirect(a,null,r,t,n,u),n.onAfterShadow(e,n,i,a,r,t,u)}}}else if(c.visible){let t=b(n,c,o,s);n.onBeforeShadow(e,n,i,a,r,t,null),e.renderBufferDirect(a,null,r,t,n,null),n.onAfterShadow(e,n,i,a,r,t,null)}}let c=n.children;for(let e=0,t=c.length;e<t;e++)S(c[e],i,a,o,s)}function C(e){e.target.removeEventListener(`dispose`,C);for(let t in l){let n=l[t],r=e.target.uuid;r in n&&(n[r].dispose(),delete n[r])}}}function hl(e,t){function n(){let t=!1,n=new rn,r=null,i=new rn(0,0,0,0);return{setMask:function(n){r!==n&&!t&&(e.colorMask(n,n,n,n),r=n)},setLocked:function(e){t=e},setClear:function(t,r,a,o,s){s===!0&&(t*=o,r*=o,a*=o),n.set(t,r,a,o),i.equals(n)===!1&&(e.clearColor(t,r,a,o),i.copy(n))},reset:function(){t=!1,r=null,i.set(-1,0,0,0)}}}function r(){let n=!1,r=!1,i=null,a=null,o=null;return{setReversed:function(e){if(r!==e){let n=t.get(`EXT_clip_control`);e?n.clipControlEXT(n.LOWER_LEFT_EXT,n.ZERO_TO_ONE_EXT):n.clipControlEXT(n.LOWER_LEFT_EXT,n.NEGATIVE_ONE_TO_ONE_EXT),r=e;let i=o;o=null,this.setClear(i)}},getReversed:function(){return r},setTest:function(t){t?F(e.DEPTH_TEST):le(e.DEPTH_TEST)},setMask:function(t){i!==t&&!n&&(e.depthMask(t),i=t)},setFunc:function(t){if(r&&(t=ft[t]),a!==t){switch(t){case 0:e.depthFunc(e.NEVER);break;case 1:e.depthFunc(e.ALWAYS);break;case 2:e.depthFunc(e.LESS);break;case 3:e.depthFunc(e.LEQUAL);break;case 4:e.depthFunc(e.EQUAL);break;case 5:e.depthFunc(e.GEQUAL);break;case 6:e.depthFunc(e.GREATER);break;case 7:e.depthFunc(e.NOTEQUAL);break;default:e.depthFunc(e.LEQUAL)}a=t}},setLocked:function(e){n=e},setClear:function(t){o!==t&&(o=t,r&&(t=1-t),e.clearDepth(t))},reset:function(){n=!1,i=null,a=null,o=null,r=!1}}}function i(){let t=!1,n=null,r=null,i=null,a=null,o=null,s=null,c=null,l=null;return{setTest:function(n){t||(n?F(e.STENCIL_TEST):le(e.STENCIL_TEST))},setMask:function(r){n!==r&&!t&&(e.stencilMask(r),n=r)},setFunc:function(t,n,o){(r!==t||i!==n||a!==o)&&(e.stencilFunc(t,n,o),r=t,i=n,a=o)},setOp:function(t,n,r){(o!==t||s!==n||c!==r)&&(e.stencilOp(t,n,r),o=t,s=n,c=r)},setLocked:function(e){t=e},setClear:function(t){l!==t&&(e.clearStencil(t),l=t)},reset:function(){t=!1,n=null,r=null,i=null,a=null,o=null,s=null,c=null,l=null}}}let a=new n,o=new r,s=new i,c=new WeakMap,l=new WeakMap,u={},d={},f={},p=new WeakMap,m=[],h=null,g=!1,_=null,v=null,y=null,b=null,x=null,S=null,C=null,w=new Y(0,0,0),T=0,E=!1,D=null,O=null,k=null,A=null,j=null,ee=e.getParameter(e.MAX_COMBINED_TEXTURE_IMAGE_UNITS),M=!1,te=0,N=e.getParameter(e.VERSION);N.indexOf(`WebGL`)===-1?N.indexOf(`OpenGL ES`)!==-1&&(te=parseFloat(/^OpenGL ES (\d)/.exec(N)[1]),M=te>=2):(te=parseFloat(/^WebGL (\d)/.exec(N)[1]),M=te>=1);let ne=null,re={},ie=e.getParameter(e.SCISSOR_BOX),ae=e.getParameter(e.VIEWPORT),oe=new rn().fromArray(ie),P=new rn().fromArray(ae);function se(t,n,r,i){let a=new Uint8Array(4),o=e.createTexture();e.bindTexture(t,o),e.texParameteri(t,e.TEXTURE_MIN_FILTER,e.NEAREST),e.texParameteri(t,e.TEXTURE_MAG_FILTER,e.NEAREST);for(let o=0;o<r;o++)t===e.TEXTURE_3D||t===e.TEXTURE_2D_ARRAY?e.texImage3D(n,0,e.RGBA,1,1,i,0,e.RGBA,e.UNSIGNED_BYTE,a):e.texImage2D(n+o,0,e.RGBA,1,1,0,e.RGBA,e.UNSIGNED_BYTE,a);return o}let ce={};ce[e.TEXTURE_2D]=se(e.TEXTURE_2D,e.TEXTURE_2D,1),ce[e.TEXTURE_CUBE_MAP]=se(e.TEXTURE_CUBE_MAP,e.TEXTURE_CUBE_MAP_POSITIVE_X,6),ce[e.TEXTURE_2D_ARRAY]=se(e.TEXTURE_2D_ARRAY,e.TEXTURE_2D_ARRAY,1,1),ce[e.TEXTURE_3D]=se(e.TEXTURE_3D,e.TEXTURE_3D,1,1),a.setClear(0,0,0,1),o.setClear(1),s.setClear(0),F(e.DEPTH_TEST),o.setFunc(3),_e(!1),ve(1),F(e.CULL_FACE),he(0);function F(t){u[t]!==!0&&(e.enable(t),u[t]=!0)}function le(t){u[t]!==!1&&(e.disable(t),u[t]=!1)}function ue(t,n){return f[t]!==n&&(e.bindFramebuffer(t,n),f[t]=n,t===e.DRAW_FRAMEBUFFER&&(f[e.FRAMEBUFFER]=n),t===e.FRAMEBUFFER&&(f[e.DRAW_FRAMEBUFFER]=n),!0)}function de(t,n){let r=m,i=!1;if(t){r=p.get(n),r===void 0&&(r=[],p.set(n,r));let a=t.textures;if(r.length!==a.length||r[0]!==e.COLOR_ATTACHMENT0){for(let t=0,n=a.length;t<n;t++)r[t]=e.COLOR_ATTACHMENT0+t;r.length=a.length,i=!0}}else r[0]!==e.BACK&&(r[0]=e.BACK,i=!0);i&&e.drawBuffers(r)}function fe(t){return h!==t&&(e.useProgram(t),h=t,!0)}let pe={100:e.FUNC_ADD,101:e.FUNC_SUBTRACT,102:e.FUNC_REVERSE_SUBTRACT};pe[103]=e.MIN,pe[104]=e.MAX;let me={200:e.ZERO,201:e.ONE,202:e.SRC_COLOR,204:e.SRC_ALPHA,210:e.SRC_ALPHA_SATURATE,208:e.DST_COLOR,206:e.DST_ALPHA,203:e.ONE_MINUS_SRC_COLOR,205:e.ONE_MINUS_SRC_ALPHA,209:e.ONE_MINUS_DST_COLOR,207:e.ONE_MINUS_DST_ALPHA,211:e.CONSTANT_COLOR,212:e.ONE_MINUS_CONSTANT_COLOR,213:e.CONSTANT_ALPHA,214:e.ONE_MINUS_CONSTANT_ALPHA};function he(t,n,r,i,a,o,s,c,l,u){if(t===0)g===!0&&(le(e.BLEND),g=!1);else if(g===!1&&(F(e.BLEND),g=!0),t!==5){if(t!==_||u!==E){if((v!==100||x!==100)&&(e.blendEquation(e.FUNC_ADD),v=100,x=100),u)switch(t){case 1:e.blendFuncSeparate(e.ONE,e.ONE_MINUS_SRC_ALPHA,e.ONE,e.ONE_MINUS_SRC_ALPHA);break;case 2:e.blendFunc(e.ONE,e.ONE);break;case 3:e.blendFuncSeparate(e.ZERO,e.ONE_MINUS_SRC_COLOR,e.ZERO,e.ONE);break;case 4:e.blendFuncSeparate(e.DST_COLOR,e.ONE_MINUS_SRC_ALPHA,e.ZERO,e.ONE);break;default:H(`WebGLState: Invalid blending: `,t)}else switch(t){case 1:e.blendFuncSeparate(e.SRC_ALPHA,e.ONE_MINUS_SRC_ALPHA,e.ONE,e.ONE_MINUS_SRC_ALPHA);break;case 2:e.blendFuncSeparate(e.SRC_ALPHA,e.ONE,e.ONE,e.ONE);break;case 3:H(`WebGLState: SubtractiveBlending requires material.premultipliedAlpha = true`);break;case 4:H(`WebGLState: MultiplyBlending requires material.premultipliedAlpha = true`);break;default:H(`WebGLState: Invalid blending: `,t)}y=null,b=null,S=null,C=null,w.set(0,0,0),T=0,_=t,E=u}}else a||=n,o||=r,s||=i,(n!==v||a!==x)&&(e.blendEquationSeparate(pe[n],pe[a]),v=n,x=a),(r!==y||i!==b||o!==S||s!==C)&&(e.blendFuncSeparate(me[r],me[i],me[o],me[s]),y=r,b=i,S=o,C=s),(c.equals(w)===!1||l!==T)&&(e.blendColor(c.r,c.g,c.b,l),w.copy(c),T=l),_=t,E=!1}function ge(t,n){t.side===2?le(e.CULL_FACE):F(e.CULL_FACE);let r=t.side===1;n&&(r=!r),_e(r),t.blending===1&&t.transparent===!1?he(0):he(t.blending,t.blendEquation,t.blendSrc,t.blendDst,t.blendEquationAlpha,t.blendSrcAlpha,t.blendDstAlpha,t.blendColor,t.blendAlpha,t.premultipliedAlpha),o.setFunc(t.depthFunc),o.setTest(t.depthTest),o.setMask(t.depthWrite),a.setMask(t.colorWrite);let i=t.stencilWrite;s.setTest(i),i&&(s.setMask(t.stencilWriteMask),s.setFunc(t.stencilFunc,t.stencilRef,t.stencilFuncMask),s.setOp(t.stencilFail,t.stencilZFail,t.stencilZPass)),be(t.polygonOffset,t.polygonOffsetFactor,t.polygonOffsetUnits),t.alphaToCoverage===!0?F(e.SAMPLE_ALPHA_TO_COVERAGE):le(e.SAMPLE_ALPHA_TO_COVERAGE)}function _e(t){D!==t&&(t?e.frontFace(e.CW):e.frontFace(e.CCW),D=t)}function ve(t){t===0?le(e.CULL_FACE):(F(e.CULL_FACE),t!==O&&(t===1?e.cullFace(e.BACK):t===2?e.cullFace(e.FRONT):e.cullFace(e.FRONT_AND_BACK))),O=t}function ye(t){t!==k&&(M&&e.lineWidth(t),k=t)}function be(t,n,r){t?(F(e.POLYGON_OFFSET_FILL),(A!==n||j!==r)&&(A=n,j=r,o.getReversed()&&(n=-n),e.polygonOffset(n,r))):le(e.POLYGON_OFFSET_FILL)}function xe(t){t?F(e.SCISSOR_TEST):le(e.SCISSOR_TEST)}function Se(t){t===void 0&&(t=e.TEXTURE0+ee-1),ne!==t&&(e.activeTexture(t),ne=t)}function Ce(t,n,r){r===void 0&&(r=ne===null?e.TEXTURE0+ee-1:ne);let i=re[r];i===void 0&&(i={type:void 0,texture:void 0},re[r]=i),(i.type!==t||i.texture!==n)&&(ne!==r&&(e.activeTexture(r),ne=r),e.bindTexture(t,n||ce[t]),i.type=t,i.texture=n)}function we(){let t=re[ne];t!==void 0&&t.type!==void 0&&(e.bindTexture(t.type,null),t.type=void 0,t.texture=void 0)}function Te(){try{e.compressedTexImage2D(...arguments)}catch(e){H(`WebGLState:`,e)}}function Ee(){try{e.compressedTexImage3D(...arguments)}catch(e){H(`WebGLState:`,e)}}function De(){try{e.texSubImage2D(...arguments)}catch(e){H(`WebGLState:`,e)}}function Oe(){try{e.texSubImage3D(...arguments)}catch(e){H(`WebGLState:`,e)}}function ke(){try{e.compressedTexSubImage2D(...arguments)}catch(e){H(`WebGLState:`,e)}}function Ae(){try{e.compressedTexSubImage3D(...arguments)}catch(e){H(`WebGLState:`,e)}}function je(){try{e.texStorage2D(...arguments)}catch(e){H(`WebGLState:`,e)}}function Me(){try{e.texStorage3D(...arguments)}catch(e){H(`WebGLState:`,e)}}function I(){try{e.texImage2D(...arguments)}catch(e){H(`WebGLState:`,e)}}function Ne(){try{e.texImage3D(...arguments)}catch(e){H(`WebGLState:`,e)}}function L(t){return d[t]===void 0?e.getParameter(t):d[t]}function Pe(t,n){d[t]!==n&&(e.pixelStorei(t,n),d[t]=n)}function R(t){oe.equals(t)===!1&&(e.scissor(t.x,t.y,t.z,t.w),oe.copy(t))}function Fe(t){P.equals(t)===!1&&(e.viewport(t.x,t.y,t.z,t.w),P.copy(t))}function z(t,n){let r=l.get(n);r===void 0&&(r=new WeakMap,l.set(n,r));let i=r.get(t);i===void 0&&(i=e.getUniformBlockIndex(n,t.name),r.set(t,i))}function B(t,n){let r=l.get(n).get(t);c.get(n)!==r&&(e.uniformBlockBinding(n,r,t.__bindingPointIndex),c.set(n,r))}function Ie(){e.disable(e.BLEND),e.disable(e.CULL_FACE),e.disable(e.DEPTH_TEST),e.disable(e.POLYGON_OFFSET_FILL),e.disable(e.SCISSOR_TEST),e.disable(e.STENCIL_TEST),e.disable(e.SAMPLE_ALPHA_TO_COVERAGE),e.blendEquation(e.FUNC_ADD),e.blendFunc(e.ONE,e.ZERO),e.blendFuncSeparate(e.ONE,e.ZERO,e.ONE,e.ZERO),e.blendColor(0,0,0,0),e.colorMask(!0,!0,!0,!0),e.clearColor(0,0,0,0),e.depthMask(!0),e.depthFunc(e.LESS),o.setReversed(!1),e.clearDepth(1),e.stencilMask(4294967295),e.stencilFunc(e.ALWAYS,0,4294967295),e.stencilOp(e.KEEP,e.KEEP,e.KEEP),e.clearStencil(0),e.cullFace(e.BACK),e.frontFace(e.CCW),e.polygonOffset(0,0),e.activeTexture(e.TEXTURE0),e.bindFramebuffer(e.FRAMEBUFFER,null),e.bindFramebuffer(e.DRAW_FRAMEBUFFER,null),e.bindFramebuffer(e.READ_FRAMEBUFFER,null),e.useProgram(null),e.lineWidth(1),e.scissor(0,0,e.canvas.width,e.canvas.height),e.viewport(0,0,e.canvas.width,e.canvas.height),e.pixelStorei(e.PACK_ALIGNMENT,4),e.pixelStorei(e.UNPACK_ALIGNMENT,4),e.pixelStorei(e.UNPACK_FLIP_Y_WEBGL,!1),e.pixelStorei(e.UNPACK_PREMULTIPLY_ALPHA_WEBGL,!1),e.pixelStorei(e.UNPACK_COLORSPACE_CONVERSION_WEBGL,e.BROWSER_DEFAULT_WEBGL),e.pixelStorei(e.PACK_ROW_LENGTH,0),e.pixelStorei(e.PACK_SKIP_PIXELS,0),e.pixelStorei(e.PACK_SKIP_ROWS,0),e.pixelStorei(e.UNPACK_ROW_LENGTH,0),e.pixelStorei(e.UNPACK_IMAGE_HEIGHT,0),e.pixelStorei(e.UNPACK_SKIP_PIXELS,0),e.pixelStorei(e.UNPACK_SKIP_ROWS,0),e.pixelStorei(e.UNPACK_SKIP_IMAGES,0),u={},d={},ne=null,re={},f={},p=new WeakMap,m=[],h=null,g=!1,_=null,v=null,y=null,b=null,x=null,S=null,C=null,w=new Y(0,0,0),T=0,E=!1,D=null,O=null,k=null,A=null,j=null,oe.set(0,0,e.canvas.width,e.canvas.height),P.set(0,0,e.canvas.width,e.canvas.height),a.reset(),o.reset(),s.reset()}return{buffers:{color:a,depth:o,stencil:s},enable:F,disable:le,bindFramebuffer:ue,drawBuffers:de,useProgram:fe,setBlending:he,setMaterial:ge,setFlipSided:_e,setCullFace:ve,setLineWidth:ye,setPolygonOffset:be,setScissorTest:xe,activeTexture:Se,bindTexture:Ce,unbindTexture:we,compressedTexImage2D:Te,compressedTexImage3D:Ee,texImage2D:I,texImage3D:Ne,pixelStorei:Pe,getParameter:L,updateUBOMapping:z,uniformBlockBinding:B,texStorage2D:je,texStorage3D:Me,texSubImage2D:De,texSubImage3D:Oe,compressedTexSubImage2D:ke,compressedTexSubImage3D:Ae,scissor:R,viewport:Fe,reset:Ie}}function gl(e,t,n,r,i,a,o){let s=t.has(`WEBGL_multisampled_render_to_texture`)?t.get(`WEBGL_multisampled_render_to_texture`):null,c=typeof navigator>`u`?!1:/OculusBrowser/g.test(navigator.userAgent),l=new W,u=new WeakMap,d=new Set,f,p=new WeakMap,m=!1;try{m=typeof OffscreenCanvas<`u`&&new OffscreenCanvas(1,1).getContext(`2d`)!==null}catch{}function w(e,t){return m?new OffscreenCanvas(e,t):at(`canvas`)}function T(e,t,n){let r=1,i=L(e);if((i.width>n||i.height>n)&&(r=n/Math.max(i.width,i.height)),r<1){if(typeof HTMLImageElement<`u`&&e instanceof HTMLImageElement||typeof HTMLCanvasElement<`u`&&e instanceof HTMLCanvasElement||typeof ImageBitmap<`u`&&e instanceof ImageBitmap||typeof VideoFrame<`u`&&e instanceof VideoFrame){let n=Math.floor(r*i.width),a=Math.floor(r*i.height);f===void 0&&(f=w(n,a));let o=t?w(n,a):f;return o.width=n,o.height=a,o.getContext(`2d`).drawImage(e,0,0,n,a),V(`WebGLRenderer: Texture has been resized from (`+i.width+`x`+i.height+`) to (`+n+`x`+a+`).`),o}return`data`in e&&V(`WebGLRenderer: Image in DataTexture is too big (`+i.width+`x`+i.height+`).`),e}return e}function E(e){return e.generateMipmaps}function D(t){e.generateMipmap(t)}function O(t){return t.isWebGLCubeRenderTarget?e.TEXTURE_CUBE_MAP:t.isWebGL3DRenderTarget?e.TEXTURE_3D:t.isWebGLArrayRenderTarget||t.isCompressedArrayTexture?e.TEXTURE_2D_ARRAY:e.TEXTURE_2D}function k(n,r,i,a,o,s=!1){if(n!==null){if(e[n]!==void 0)return e[n];V(`WebGLRenderer: Attempt to use non-existing WebGL internal format '`+n+`'`)}let c;a&&(c=t.get(`EXT_texture_norm16`),c||V(`WebGLRenderer: Unable to use normalized textures without EXT_texture_norm16 extension`));let l=r;if(r===e.RED&&(i===e.FLOAT&&(l=e.R32F),i===e.HALF_FLOAT&&(l=e.R16F),i===e.UNSIGNED_BYTE&&(l=e.R8),i===e.UNSIGNED_SHORT&&c&&(l=c.R16_EXT),i===e.SHORT&&c&&(l=c.R16_SNORM_EXT)),r===e.RED_INTEGER&&(i===e.UNSIGNED_BYTE&&(l=e.R8UI),i===e.UNSIGNED_SHORT&&(l=e.R16UI),i===e.UNSIGNED_INT&&(l=e.R32UI),i===e.BYTE&&(l=e.R8I),i===e.SHORT&&(l=e.R16I),i===e.INT&&(l=e.R32I)),r===e.RG&&(i===e.FLOAT&&(l=e.RG32F),i===e.HALF_FLOAT&&(l=e.RG16F),i===e.UNSIGNED_BYTE&&(l=e.RG8),i===e.UNSIGNED_SHORT&&c&&(l=c.RG16_EXT),i===e.SHORT&&c&&(l=c.RG16_SNORM_EXT)),r===e.RG_INTEGER&&(i===e.UNSIGNED_BYTE&&(l=e.RG8UI),i===e.UNSIGNED_SHORT&&(l=e.RG16UI),i===e.UNSIGNED_INT&&(l=e.RG32UI),i===e.BYTE&&(l=e.RG8I),i===e.SHORT&&(l=e.RG16I),i===e.INT&&(l=e.RG32I)),r===e.RGB_INTEGER&&(i===e.UNSIGNED_BYTE&&(l=e.RGB8UI),i===e.UNSIGNED_SHORT&&(l=e.RGB16UI),i===e.UNSIGNED_INT&&(l=e.RGB32UI),i===e.BYTE&&(l=e.RGB8I),i===e.SHORT&&(l=e.RGB16I),i===e.INT&&(l=e.RGB32I)),r===e.RGBA_INTEGER&&(i===e.UNSIGNED_BYTE&&(l=e.RGBA8UI),i===e.UNSIGNED_SHORT&&(l=e.RGBA16UI),i===e.UNSIGNED_INT&&(l=e.RGBA32UI),i===e.BYTE&&(l=e.RGBA8I),i===e.SHORT&&(l=e.RGBA16I),i===e.INT&&(l=e.RGBA32I)),r===e.RGB&&(i===e.UNSIGNED_SHORT&&c&&(l=c.RGB16_EXT),i===e.SHORT&&c&&(l=c.RGB16_SNORM_EXT),i===e.UNSIGNED_INT_5_9_9_9_REV&&(l=e.RGB9_E5),i===e.UNSIGNED_INT_10F_11F_11F_REV&&(l=e.R11F_G11F_B10F)),r===e.RGBA){let t=s?Qe:q.getTransfer(o);i===e.FLOAT&&(l=e.RGBA32F),i===e.HALF_FLOAT&&(l=e.RGBA16F),i===e.UNSIGNED_BYTE&&(l=t===`srgb`?e.SRGB8_ALPHA8:e.RGBA8),i===e.UNSIGNED_SHORT&&c&&(l=c.RGBA16_EXT),i===e.SHORT&&c&&(l=c.RGBA16_SNORM_EXT),i===e.UNSIGNED_SHORT_4_4_4_4&&(l=e.RGBA4),i===e.UNSIGNED_SHORT_5_5_5_1&&(l=e.RGB5_A1)}return(l===e.R16F||l===e.R32F||l===e.RG16F||l===e.RG32F||l===e.RGBA16F||l===e.RGBA32F)&&t.get(`EXT_color_buffer_float`),l}function A(t,n){let r;return t?n===null||n===1014||n===1020?r=e.DEPTH24_STENCIL8:n===1015?r=e.DEPTH32F_STENCIL8:n===1012&&(r=e.DEPTH24_STENCIL8,V(`DepthTexture: 16 bit depth attachment is not supported with stencil. Using 24-bit attachment.`)):n===null||n===1014||n===1020?r=e.DEPTH_COMPONENT24:n===1015?r=e.DEPTH_COMPONENT32F:n===1012&&(r=e.DEPTH_COMPONENT16),r}function j(e,t){return E(e)===!0||e.isFramebufferTexture&&e.minFilter!==1003&&e.minFilter!==1006?Math.log2(Math.max(t.width,t.height))+1:e.mipmaps!==void 0&&e.mipmaps.length>0?e.mipmaps.length:e.isCompressedTexture&&Array.isArray(e.image)?t.mipmaps.length:1}function ee(e){let t=e.target;t.removeEventListener(`dispose`,ee),te(t),t.isVideoTexture&&u.delete(t),t.isHTMLTexture&&d.delete(t)}function M(e){let t=e.target;t.removeEventListener(`dispose`,M),ne(t)}function te(e){let t=r.get(e);if(t.__webglInit===void 0)return;let n=e.source,i=p.get(n);if(i){let r=i[t.__cacheKey];r.usedTimes--,r.usedTimes===0&&N(e),Object.keys(i).length===0&&p.delete(n)}r.remove(e)}function N(t){let n=r.get(t);e.deleteTexture(n.__webglTexture);let i=t.source,a=p.get(i);delete a[n.__cacheKey],o.memory.textures--}function ne(t){let n=r.get(t);if(t.depthTexture&&(t.depthTexture.dispose(),r.remove(t.depthTexture)),t.isWebGLCubeRenderTarget)for(let t=0;t<6;t++){if(Array.isArray(n.__webglFramebuffer[t]))for(let r=0;r<n.__webglFramebuffer[t].length;r++)e.deleteFramebuffer(n.__webglFramebuffer[t][r]);else e.deleteFramebuffer(n.__webglFramebuffer[t]);n.__webglDepthbuffer&&e.deleteRenderbuffer(n.__webglDepthbuffer[t])}else{if(Array.isArray(n.__webglFramebuffer))for(let t=0;t<n.__webglFramebuffer.length;t++)e.deleteFramebuffer(n.__webglFramebuffer[t]);else e.deleteFramebuffer(n.__webglFramebuffer);if(n.__webglDepthbuffer&&e.deleteRenderbuffer(n.__webglDepthbuffer),n.__webglMultisampledFramebuffer&&e.deleteFramebuffer(n.__webglMultisampledFramebuffer),n.__webglColorRenderbuffer)for(let t=0;t<n.__webglColorRenderbuffer.length;t++)n.__webglColorRenderbuffer[t]&&e.deleteRenderbuffer(n.__webglColorRenderbuffer[t]);n.__webglDepthRenderbuffer&&e.deleteRenderbuffer(n.__webglDepthRenderbuffer)}let i=t.textures;for(let t=0,n=i.length;t<n;t++){let n=r.get(i[t]);n.__webglTexture&&(e.deleteTexture(n.__webglTexture),o.memory.textures--),r.remove(i[t])}r.remove(t)}let re=0;function ie(){re=0}function ae(){return re}function oe(e){re=e}function se(){let e=re;return e>=i.maxTextures&&V(`WebGLTextures: Trying to use `+(e+1)+` texture units while this GPU supports only `+i.maxTextures),re+=1,e}function ce(e){let t=[];return t.push(e.wrapS),t.push(e.wrapT),t.push(e.wrapR||0),t.push(e.magFilter),t.push(e.minFilter),t.push(e.anisotropy),t.push(e.internalFormat),t.push(e.format),t.push(e.type),t.push(e.generateMipmaps),t.push(e.premultiplyAlpha),t.push(e.flipY),t.push(e.unpackAlignment),t.push(e.colorSpace),t.join()}function F(t,i){let a=r.get(t);if(t.isVideoTexture&&I(t),t.isRenderTargetTexture===!1&&t.isExternalTexture!==!0&&t.version>0&&a.__version!==t.version){let e=t.image;if(e===null)V(`WebGLRenderer: Texture marked for update but no image data found.`);else if(e.complete===!1)V(`WebGLRenderer: Texture marked for update but image is incomplete`);else{ye(a,t,i);return}}else t.isExternalTexture&&(a.__webglTexture=t.sourceTexture?t.sourceTexture:null);n.bindTexture(e.TEXTURE_2D,a.__webglTexture,e.TEXTURE0+i)}function le(t,i){let a=r.get(t);t.isRenderTargetTexture===!1&&t.version>0&&a.__version!==t.version?ye(a,t,i):(t.isExternalTexture&&(a.__webglTexture=t.sourceTexture?t.sourceTexture:null),n.bindTexture(e.TEXTURE_2D_ARRAY,a.__webglTexture,e.TEXTURE0+i))}function ue(t,i){let a=r.get(t);t.isRenderTargetTexture===!1&&t.version>0&&a.__version!==t.version?ye(a,t,i):n.bindTexture(e.TEXTURE_3D,a.__webglTexture,e.TEXTURE0+i)}function de(t,i){let a=r.get(t);t.isCubeDepthTexture!==!0&&t.version>0&&a.__version!==t.version?be(a,t,i):n.bindTexture(e.TEXTURE_CUBE_MAP,a.__webglTexture,e.TEXTURE0+i)}let fe={[h]:e.REPEAT,[g]:e.CLAMP_TO_EDGE,[_]:e.MIRRORED_REPEAT},pe={[v]:e.NEAREST,[y]:e.NEAREST_MIPMAP_NEAREST,[b]:e.NEAREST_MIPMAP_LINEAR,[x]:e.LINEAR,[S]:e.LINEAR_MIPMAP_NEAREST,[C]:e.LINEAR_MIPMAP_LINEAR},me={512:e.NEVER,519:e.ALWAYS,513:e.LESS,515:e.LEQUAL,514:e.EQUAL,518:e.GEQUAL,516:e.GREATER,517:e.NOTEQUAL};function he(n,a){if(a.type===1015&&t.has(`OES_texture_float_linear`)===!1&&(a.magFilter===1006||a.magFilter===1007||a.magFilter===1005||a.magFilter===1008||a.minFilter===1006||a.minFilter===1007||a.minFilter===1005||a.minFilter===1008)&&V(`WebGLRenderer: Unable to use linear filtering with floating point textures. OES_texture_float_linear not supported on this device.`),e.texParameteri(n,e.TEXTURE_WRAP_S,fe[a.wrapS]),e.texParameteri(n,e.TEXTURE_WRAP_T,fe[a.wrapT]),(n===e.TEXTURE_3D||n===e.TEXTURE_2D_ARRAY)&&e.texParameteri(n,e.TEXTURE_WRAP_R,fe[a.wrapR]),e.texParameteri(n,e.TEXTURE_MAG_FILTER,pe[a.magFilter]),e.texParameteri(n,e.TEXTURE_MIN_FILTER,pe[a.minFilter]),a.compareFunction&&(e.texParameteri(n,e.TEXTURE_COMPARE_MODE,e.COMPARE_REF_TO_TEXTURE),e.texParameteri(n,e.TEXTURE_COMPARE_FUNC,me[a.compareFunction])),t.has(`EXT_texture_filter_anisotropic`)===!0){if(a.magFilter===1003||a.minFilter!==1005&&a.minFilter!==1008||a.type===1015&&t.has(`OES_texture_float_linear`)===!1)return;if(a.anisotropy>1||r.get(a).__currentAnisotropy){let o=t.get(`EXT_texture_filter_anisotropic`);e.texParameterf(n,o.TEXTURE_MAX_ANISOTROPY_EXT,Math.min(a.anisotropy,i.getMaxAnisotropy())),r.get(a).__currentAnisotropy=a.anisotropy}}}function ge(t,n){let r=!1;t.__webglInit===void 0&&(t.__webglInit=!0,n.addEventListener(`dispose`,ee));let i=n.source,a=p.get(i);a===void 0&&(a={},p.set(i,a));let s=ce(n);if(s!==t.__cacheKey){a[s]===void 0&&(a[s]={texture:e.createTexture(),usedTimes:0},o.memory.textures++,r=!0),a[s].usedTimes++;let i=a[t.__cacheKey];i!==void 0&&(a[t.__cacheKey].usedTimes--,i.usedTimes===0&&N(n)),t.__cacheKey=s,t.__webglTexture=a[s].texture}return r}function _e(e,t,n){return Math.floor(Math.floor(e/n)/t)}function ve(t,r,i,a){let o=t.updateRanges;if(o.length===0)n.texSubImage2D(e.TEXTURE_2D,0,0,0,r.width,r.height,i,a,r.data);else{o.sort((e,t)=>e.start-t.start);let s=0;for(let e=1;e<o.length;e++){let t=o[s],n=o[e],i=t.start+t.count,a=_e(n.start,r.width,4),c=_e(t.start,r.width,4);n.start<=i+1&&a===c&&_e(n.start+n.count-1,r.width,4)===a?t.count=Math.max(t.count,n.start+n.count-t.start):(++s,o[s]=n)}o.length=s+1;let c=n.getParameter(e.UNPACK_ROW_LENGTH),l=n.getParameter(e.UNPACK_SKIP_PIXELS),u=n.getParameter(e.UNPACK_SKIP_ROWS);n.pixelStorei(e.UNPACK_ROW_LENGTH,r.width);for(let t=0,s=o.length;t<s;t++){let s=o[t],c=Math.floor(s.start/4),l=Math.ceil(s.count/4),u=c%r.width,d=Math.floor(c/r.width),f=l;n.pixelStorei(e.UNPACK_SKIP_PIXELS,u),n.pixelStorei(e.UNPACK_SKIP_ROWS,d),n.texSubImage2D(e.TEXTURE_2D,0,u,d,f,1,i,a,r.data)}t.clearUpdateRanges(),n.pixelStorei(e.UNPACK_ROW_LENGTH,c),n.pixelStorei(e.UNPACK_SKIP_PIXELS,l),n.pixelStorei(e.UNPACK_SKIP_ROWS,u)}}function ye(t,o,s){let c=e.TEXTURE_2D;(o.isDataArrayTexture||o.isCompressedArrayTexture)&&(c=e.TEXTURE_2D_ARRAY),o.isData3DTexture&&(c=e.TEXTURE_3D);let l=ge(t,o),u=o.source;n.bindTexture(c,t.__webglTexture,e.TEXTURE0+s);let f=r.get(u);if(u.version!==f.__version||l===!0){if(n.activeTexture(e.TEXTURE0+s),!(typeof ImageBitmap<`u`&&o.image instanceof ImageBitmap)){let t=q.getPrimaries(q.workingColorSpace),r=o.colorSpace===``?null:q.getPrimaries(o.colorSpace),i=o.colorSpace===``||t===r?e.NONE:e.BROWSER_DEFAULT_WEBGL;n.pixelStorei(e.UNPACK_FLIP_Y_WEBGL,o.flipY),n.pixelStorei(e.UNPACK_PREMULTIPLY_ALPHA_WEBGL,o.premultiplyAlpha),n.pixelStorei(e.UNPACK_COLORSPACE_CONVERSION_WEBGL,i)}n.pixelStorei(e.UNPACK_ALIGNMENT,o.unpackAlignment);let t=T(o.image,!1,i.maxTextureSize);t=Ne(o,t);let r=a.convert(o.format,o.colorSpace),p=a.convert(o.type),m=k(o.internalFormat,r,p,o.normalized,o.colorSpace,o.isVideoTexture);he(c,o);let h,g=o.mipmaps,_=o.isVideoTexture!==!0,v=f.__version===void 0||l===!0,y=u.dataReady,b=j(o,t);if(o.isDepthTexture)m=A(o.format===P,o.type),v&&(_?n.texStorage2D(e.TEXTURE_2D,1,m,t.width,t.height):n.texImage2D(e.TEXTURE_2D,0,m,t.width,t.height,0,r,p,null));else if(o.isDataTexture){if(g.length>0){_&&v&&n.texStorage2D(e.TEXTURE_2D,b,m,g[0].width,g[0].height);for(let t=0,i=g.length;t<i;t++)h=g[t],_?y&&n.texSubImage2D(e.TEXTURE_2D,t,0,0,h.width,h.height,r,p,h.data):n.texImage2D(e.TEXTURE_2D,t,m,h.width,h.height,0,r,p,h.data);o.generateMipmaps=!1}else _?(v&&n.texStorage2D(e.TEXTURE_2D,b,m,t.width,t.height),y&&ve(o,t,r,p)):n.texImage2D(e.TEXTURE_2D,0,m,t.width,t.height,0,r,p,t.data)}else if(o.isCompressedTexture){if(o.isCompressedArrayTexture){_&&v&&n.texStorage3D(e.TEXTURE_2D_ARRAY,b,m,g[0].width,g[0].height,t.depth);for(let i=0,a=g.length;i<a;i++)if(h=g[i],o.format!==1023){if(r!==null){if(_){if(y){if(o.layerUpdates.size>0){let t=uo(h.width,h.height,o.format,o.type);for(let a of o.layerUpdates){let o=h.data.subarray(a*t/h.data.BYTES_PER_ELEMENT,(a+1)*t/h.data.BYTES_PER_ELEMENT);n.compressedTexSubImage3D(e.TEXTURE_2D_ARRAY,i,0,0,a,h.width,h.height,1,r,o)}}else n.compressedTexSubImage3D(e.TEXTURE_2D_ARRAY,i,0,0,0,h.width,h.height,t.depth,r,h.data)}}else n.compressedTexImage3D(e.TEXTURE_2D_ARRAY,i,m,h.width,h.height,t.depth,0,h.data,0,0)}else V(`WebGLRenderer: Attempt to load unsupported compressed texture format in .uploadTexture()`)}else _?y&&n.texSubImage3D(e.TEXTURE_2D_ARRAY,i,0,0,0,h.width,h.height,t.depth,r,p,h.data):n.texImage3D(e.TEXTURE_2D_ARRAY,i,m,h.width,h.height,t.depth,0,r,p,h.data);o.layerUpdates.size>0&&o.clearLayerUpdates()}else{_&&v&&n.texStorage2D(e.TEXTURE_2D,b,m,g[0].width,g[0].height);for(let t=0,i=g.length;t<i;t++)h=g[t],o.format===1023?_?y&&n.texSubImage2D(e.TEXTURE_2D,t,0,0,h.width,h.height,r,p,h.data):n.texImage2D(e.TEXTURE_2D,t,m,h.width,h.height,0,r,p,h.data):r===null?V(`WebGLRenderer: Attempt to load unsupported compressed texture format in .uploadTexture()`):_?y&&n.compressedTexSubImage2D(e.TEXTURE_2D,t,0,0,h.width,h.height,r,h.data):n.compressedTexImage2D(e.TEXTURE_2D,t,m,h.width,h.height,0,h.data)}}else if(o.isDataArrayTexture){if(_){if(v&&n.texStorage3D(e.TEXTURE_2D_ARRAY,b,m,t.width,t.height,t.depth),y){if(o.layerUpdates.size>0){let i=uo(t.width,t.height,o.format,o.type);for(let a of o.layerUpdates){let o=t.data.subarray(a*i/t.data.BYTES_PER_ELEMENT,(a+1)*i/t.data.BYTES_PER_ELEMENT);n.texSubImage3D(e.TEXTURE_2D_ARRAY,0,0,0,a,t.width,t.height,1,r,p,o)}o.clearLayerUpdates()}else n.texSubImage3D(e.TEXTURE_2D_ARRAY,0,0,0,0,t.width,t.height,t.depth,r,p,t.data)}}else n.texImage3D(e.TEXTURE_2D_ARRAY,0,m,t.width,t.height,t.depth,0,r,p,t.data)}else if(o.isData3DTexture)_?(v&&n.texStorage3D(e.TEXTURE_3D,b,m,t.width,t.height,t.depth),y&&n.texSubImage3D(e.TEXTURE_3D,0,0,0,0,t.width,t.height,t.depth,r,p,t.data)):n.texImage3D(e.TEXTURE_3D,0,m,t.width,t.height,t.depth,0,r,p,t.data);else if(o.isFramebufferTexture){if(v){if(_)n.texStorage2D(e.TEXTURE_2D,b,m,t.width,t.height);else{let i=t.width,a=t.height;for(let t=0;t<b;t++)n.texImage2D(e.TEXTURE_2D,t,m,i,a,0,r,p,null),i>>=1,a>>=1}}}else if(o.isHTMLTexture){if(`texElementImage2D`in e){let n=e.canvas;if(n.hasAttribute(`layoutsubtree`)||n.setAttribute(`layoutsubtree`,`true`),t.parentNode!==n){n.appendChild(t),d.add(o),n.onpaint=e=>{let t=e.changedElements;for(let e of d)t.includes(e.image)&&(e.needsUpdate=!0)},n.requestPaint();return}if(e.texElementImage2D.length===3)e.texElementImage2D(e.TEXTURE_2D,e.RGBA8,t);else{let n=e.RGBA,r=e.RGBA,i=e.UNSIGNED_BYTE;e.texElementImage2D(e.TEXTURE_2D,0,n,r,i,t)}e.texParameteri(e.TEXTURE_2D,e.TEXTURE_MIN_FILTER,e.LINEAR),e.texParameteri(e.TEXTURE_2D,e.TEXTURE_WRAP_S,e.CLAMP_TO_EDGE),e.texParameteri(e.TEXTURE_2D,e.TEXTURE_WRAP_T,e.CLAMP_TO_EDGE)}}else if(g.length>0){if(_&&v){let t=L(g[0]);n.texStorage2D(e.TEXTURE_2D,b,m,t.width,t.height)}for(let t=0,i=g.length;t<i;t++)h=g[t],_?y&&n.texSubImage2D(e.TEXTURE_2D,t,0,0,r,p,h):n.texImage2D(e.TEXTURE_2D,t,m,r,p,h);o.generateMipmaps=!1}else if(_){if(v){let r=L(t);n.texStorage2D(e.TEXTURE_2D,b,m,r.width,r.height)}y&&n.texSubImage2D(e.TEXTURE_2D,0,0,0,r,p,t)}else n.texImage2D(e.TEXTURE_2D,0,m,r,p,t);E(o)&&D(c),f.__version=u.version,o.onUpdate&&o.onUpdate(o)}t.__version=o.version}function be(t,o,s){if(o.image.length!==6)return;let c=ge(t,o),l=o.source;n.bindTexture(e.TEXTURE_CUBE_MAP,t.__webglTexture,e.TEXTURE0+s);let u=r.get(l);if(l.version!==u.__version||c===!0){n.activeTexture(e.TEXTURE0+s);let t=q.getPrimaries(q.workingColorSpace),r=o.colorSpace===``?null:q.getPrimaries(o.colorSpace),d=o.colorSpace===``||t===r?e.NONE:e.BROWSER_DEFAULT_WEBGL;n.pixelStorei(e.UNPACK_FLIP_Y_WEBGL,o.flipY),n.pixelStorei(e.UNPACK_PREMULTIPLY_ALPHA_WEBGL,o.premultiplyAlpha),n.pixelStorei(e.UNPACK_ALIGNMENT,o.unpackAlignment),n.pixelStorei(e.UNPACK_COLORSPACE_CONVERSION_WEBGL,d);let f=o.isCompressedTexture||o.image[0].isCompressedTexture,p=o.image[0]&&o.image[0].isDataTexture,m=[];for(let e=0;e<6;e++)!f&&!p?m[e]=T(o.image[e],!0,i.maxCubemapSize):m[e]=p?o.image[e].image:o.image[e],m[e]=Ne(o,m[e]);let h=m[0],g=a.convert(o.format,o.colorSpace),_=a.convert(o.type),v=k(o.internalFormat,g,_,o.normalized,o.colorSpace),y=o.isVideoTexture!==!0,b=u.__version===void 0||c===!0,x=l.dataReady,S=j(o,h);he(e.TEXTURE_CUBE_MAP,o);let C;if(f){y&&b&&n.texStorage2D(e.TEXTURE_CUBE_MAP,S,v,h.width,h.height);for(let t=0;t<6;t++){C=m[t].mipmaps;for(let r=0;r<C.length;r++){let i=C[r];o.format===1023?y?x&&n.texSubImage2D(e.TEXTURE_CUBE_MAP_POSITIVE_X+t,r,0,0,i.width,i.height,g,_,i.data):n.texImage2D(e.TEXTURE_CUBE_MAP_POSITIVE_X+t,r,v,i.width,i.height,0,g,_,i.data):g===null?V(`WebGLRenderer: Attempt to load unsupported compressed texture format in .setTextureCube()`):y?x&&n.compressedTexSubImage2D(e.TEXTURE_CUBE_MAP_POSITIVE_X+t,r,0,0,i.width,i.height,g,i.data):n.compressedTexImage2D(e.TEXTURE_CUBE_MAP_POSITIVE_X+t,r,v,i.width,i.height,0,i.data)}}}else{if(C=o.mipmaps,y&&b){C.length>0&&S++;let t=L(m[0]);n.texStorage2D(e.TEXTURE_CUBE_MAP,S,v,t.width,t.height)}for(let t=0;t<6;t++)if(p){y?x&&n.texSubImage2D(e.TEXTURE_CUBE_MAP_POSITIVE_X+t,0,0,0,m[t].width,m[t].height,g,_,m[t].data):n.texImage2D(e.TEXTURE_CUBE_MAP_POSITIVE_X+t,0,v,m[t].width,m[t].height,0,g,_,m[t].data);for(let r=0;r<C.length;r++){let i=C[r].image[t].image;y?x&&n.texSubImage2D(e.TEXTURE_CUBE_MAP_POSITIVE_X+t,r+1,0,0,i.width,i.height,g,_,i.data):n.texImage2D(e.TEXTURE_CUBE_MAP_POSITIVE_X+t,r+1,v,i.width,i.height,0,g,_,i.data)}}else{y?x&&n.texSubImage2D(e.TEXTURE_CUBE_MAP_POSITIVE_X+t,0,0,0,g,_,m[t]):n.texImage2D(e.TEXTURE_CUBE_MAP_POSITIVE_X+t,0,v,g,_,m[t]);for(let r=0;r<C.length;r++){let i=C[r];y?x&&n.texSubImage2D(e.TEXTURE_CUBE_MAP_POSITIVE_X+t,r+1,0,0,g,_,i.image[t]):n.texImage2D(e.TEXTURE_CUBE_MAP_POSITIVE_X+t,r+1,v,g,_,i.image[t])}}}E(o)&&D(e.TEXTURE_CUBE_MAP),u.__version=l.version,o.onUpdate&&o.onUpdate(o)}t.__version=o.version}function xe(t,i,o,c,l,u){let d=a.convert(o.format,o.colorSpace),f=a.convert(o.type),p=k(o.internalFormat,d,f,o.normalized,o.colorSpace),m=r.get(i),h=r.get(o);if(h.__renderTarget=i,!m.__hasExternalTextures){let t=Math.max(1,i.width>>u),r=Math.max(1,i.height>>u);l===e.TEXTURE_3D||l===e.TEXTURE_2D_ARRAY?n.texImage3D(l,u,p,t,r,i.depth,0,d,f,null):n.texImage2D(l,u,p,t,r,0,d,f,null)}n.bindFramebuffer(e.FRAMEBUFFER,t),Me(i)?s.framebufferTexture2DMultisampleEXT(e.FRAMEBUFFER,c,l,h.__webglTexture,0,je(i)):(l===e.TEXTURE_2D||l>=e.TEXTURE_CUBE_MAP_POSITIVE_X&&l<=e.TEXTURE_CUBE_MAP_NEGATIVE_Z)&&e.framebufferTexture2D(e.FRAMEBUFFER,c,l,h.__webglTexture,u),n.bindFramebuffer(e.FRAMEBUFFER,null)}function Se(t,n,r){if(e.bindRenderbuffer(e.RENDERBUFFER,t),n.depthBuffer){let i=n.depthTexture,a=i&&i.isDepthTexture?i.type:null,o=A(n.stencilBuffer,a),c=n.stencilBuffer?e.DEPTH_STENCIL_ATTACHMENT:e.DEPTH_ATTACHMENT;Me(n)?s.renderbufferStorageMultisampleEXT(e.RENDERBUFFER,je(n),o,n.width,n.height):r?e.renderbufferStorageMultisample(e.RENDERBUFFER,je(n),o,n.width,n.height):e.renderbufferStorage(e.RENDERBUFFER,o,n.width,n.height),e.framebufferRenderbuffer(e.FRAMEBUFFER,c,e.RENDERBUFFER,t)}else{let t=n.textures;for(let i=0;i<t.length;i++){let o=t[i],c=a.convert(o.format,o.colorSpace),l=a.convert(o.type),u=k(o.internalFormat,c,l,o.normalized,o.colorSpace);Me(n)?s.renderbufferStorageMultisampleEXT(e.RENDERBUFFER,je(n),u,n.width,n.height):r?e.renderbufferStorageMultisample(e.RENDERBUFFER,je(n),u,n.width,n.height):e.renderbufferStorage(e.RENDERBUFFER,u,n.width,n.height)}}e.bindRenderbuffer(e.RENDERBUFFER,null)}function Ce(t,i,o){let c=i.isWebGLCubeRenderTarget===!0;if(n.bindFramebuffer(e.FRAMEBUFFER,t),!(i.depthTexture&&i.depthTexture.isDepthTexture))throw Error(`THREE.WebGLTextures: renderTarget.depthTexture must be an instance of THREE.DepthTexture.`);let l=r.get(i.depthTexture);if(l.__renderTarget=i,(!l.__webglTexture||i.depthTexture.image.width!==i.width||i.depthTexture.image.height!==i.height)&&(i.depthTexture.image.width=i.width,i.depthTexture.image.height=i.height,i.depthTexture.needsUpdate=!0),c){if(l.__webglInit===void 0&&(l.__webglInit=!0,i.depthTexture.addEventListener(`dispose`,ee)),l.__webglTexture===void 0){l.__webglTexture=e.createTexture(),n.bindTexture(e.TEXTURE_CUBE_MAP,l.__webglTexture),he(e.TEXTURE_CUBE_MAP,i.depthTexture);let t=a.convert(i.depthTexture.format),r=a.convert(i.depthTexture.type),o;i.depthTexture.format===1026?o=e.DEPTH_COMPONENT24:i.depthTexture.format===1027&&(o=e.DEPTH24_STENCIL8);for(let n=0;n<6;n++)e.texImage2D(e.TEXTURE_CUBE_MAP_POSITIVE_X+n,0,o,i.width,i.height,0,t,r,null)}}else F(i.depthTexture,0);let u=l.__webglTexture,d=je(i),f=c?e.TEXTURE_CUBE_MAP_POSITIVE_X+o:e.TEXTURE_2D,p=i.depthTexture.format===1027?e.DEPTH_STENCIL_ATTACHMENT:e.DEPTH_ATTACHMENT;if(i.depthTexture.format===1026)Me(i)?s.framebufferTexture2DMultisampleEXT(e.FRAMEBUFFER,p,f,u,0,d):e.framebufferTexture2D(e.FRAMEBUFFER,p,f,u,0);else if(i.depthTexture.format===1027)Me(i)?s.framebufferTexture2DMultisampleEXT(e.FRAMEBUFFER,p,f,u,0,d):e.framebufferTexture2D(e.FRAMEBUFFER,p,f,u,0);else throw Error(`THREE.WebGLTextures: Unknown depthTexture format.`)}function we(t){let i=r.get(t),a=t.isWebGLCubeRenderTarget===!0;if(i.__boundDepthTexture!==t.depthTexture){let e=t.depthTexture;if(i.__depthDisposeCallback&&i.__depthDisposeCallback(),e){let t=()=>{delete i.__boundDepthTexture,delete i.__depthDisposeCallback,e.removeEventListener(`dispose`,t)};e.addEventListener(`dispose`,t),i.__depthDisposeCallback=t}i.__boundDepthTexture=e}if(t.depthTexture&&!i.__autoAllocateDepthBuffer){if(a)for(let e=0;e<6;e++)Ce(i.__webglFramebuffer[e],t,e);else{let e=t.texture.mipmaps;e&&e.length>0?Ce(i.__webglFramebuffer[0],t,0):Ce(i.__webglFramebuffer,t,0)}}else if(a){i.__webglDepthbuffer=[];for(let r=0;r<6;r++)if(n.bindFramebuffer(e.FRAMEBUFFER,i.__webglFramebuffer[r]),i.__webglDepthbuffer[r]===void 0)i.__webglDepthbuffer[r]=e.createRenderbuffer(),Se(i.__webglDepthbuffer[r],t,!1);else{let n=t.stencilBuffer?e.DEPTH_STENCIL_ATTACHMENT:e.DEPTH_ATTACHMENT,a=i.__webglDepthbuffer[r];e.bindRenderbuffer(e.RENDERBUFFER,a),e.framebufferRenderbuffer(e.FRAMEBUFFER,n,e.RENDERBUFFER,a)}}else{let r=t.texture.mipmaps;if(r&&r.length>0?n.bindFramebuffer(e.FRAMEBUFFER,i.__webglFramebuffer[0]):n.bindFramebuffer(e.FRAMEBUFFER,i.__webglFramebuffer),i.__webglDepthbuffer===void 0)i.__webglDepthbuffer=e.createRenderbuffer(),Se(i.__webglDepthbuffer,t,!1);else{let n=t.stencilBuffer?e.DEPTH_STENCIL_ATTACHMENT:e.DEPTH_ATTACHMENT,r=i.__webglDepthbuffer;e.bindRenderbuffer(e.RENDERBUFFER,r),e.framebufferRenderbuffer(e.FRAMEBUFFER,n,e.RENDERBUFFER,r)}}n.bindFramebuffer(e.FRAMEBUFFER,null)}function Te(t,n,i){let a=r.get(t);n!==void 0&&xe(a.__webglFramebuffer,t,t.texture,e.COLOR_ATTACHMENT0,e.TEXTURE_2D,0),i!==void 0&&we(t)}function Ee(t){let i=t.texture,s=r.get(t),c=r.get(i);t.addEventListener(`dispose`,M);let l=t.textures,u=t.isWebGLCubeRenderTarget===!0,d=l.length>1;if(d||(c.__webglTexture===void 0&&(c.__webglTexture=e.createTexture()),c.__version=i.version,o.memory.textures++),u){s.__webglFramebuffer=[];for(let t=0;t<6;t++)if(i.mipmaps&&i.mipmaps.length>0){s.__webglFramebuffer[t]=[];for(let n=0;n<i.mipmaps.length;n++)s.__webglFramebuffer[t][n]=e.createFramebuffer()}else s.__webglFramebuffer[t]=e.createFramebuffer()}else{if(i.mipmaps&&i.mipmaps.length>0){s.__webglFramebuffer=[];for(let t=0;t<i.mipmaps.length;t++)s.__webglFramebuffer[t]=e.createFramebuffer()}else s.__webglFramebuffer=e.createFramebuffer();if(d)for(let t=0,n=l.length;t<n;t++){let n=r.get(l[t]);n.__webglTexture===void 0&&(n.__webglTexture=e.createTexture(),o.memory.textures++)}if(t.samples>0&&Me(t)===!1){s.__webglMultisampledFramebuffer=e.createFramebuffer(),s.__webglColorRenderbuffer=[],n.bindFramebuffer(e.FRAMEBUFFER,s.__webglMultisampledFramebuffer);for(let n=0;n<l.length;n++){let r=l[n];s.__webglColorRenderbuffer[n]=e.createRenderbuffer(),e.bindRenderbuffer(e.RENDERBUFFER,s.__webglColorRenderbuffer[n]);let i=a.convert(r.format,r.colorSpace),o=a.convert(r.type),c=k(r.internalFormat,i,o,r.normalized,r.colorSpace,t.isXRRenderTarget===!0),u=je(t);e.renderbufferStorageMultisample(e.RENDERBUFFER,u,c,t.width,t.height),e.framebufferRenderbuffer(e.FRAMEBUFFER,e.COLOR_ATTACHMENT0+n,e.RENDERBUFFER,s.__webglColorRenderbuffer[n])}e.bindRenderbuffer(e.RENDERBUFFER,null),t.depthBuffer&&(s.__webglDepthRenderbuffer=e.createRenderbuffer(),Se(s.__webglDepthRenderbuffer,t,!0)),n.bindFramebuffer(e.FRAMEBUFFER,null)}}if(u){n.bindTexture(e.TEXTURE_CUBE_MAP,c.__webglTexture),he(e.TEXTURE_CUBE_MAP,i);for(let n=0;n<6;n++)if(i.mipmaps&&i.mipmaps.length>0)for(let r=0;r<i.mipmaps.length;r++)xe(s.__webglFramebuffer[n][r],t,i,e.COLOR_ATTACHMENT0,e.TEXTURE_CUBE_MAP_POSITIVE_X+n,r);else xe(s.__webglFramebuffer[n],t,i,e.COLOR_ATTACHMENT0,e.TEXTURE_CUBE_MAP_POSITIVE_X+n,0);E(i)&&D(e.TEXTURE_CUBE_MAP),n.unbindTexture()}else if(d){for(let i=0,a=l.length;i<a;i++){let a=l[i],o=r.get(a),c=e.TEXTURE_2D;(t.isWebGL3DRenderTarget||t.isWebGLArrayRenderTarget)&&(c=t.isWebGL3DRenderTarget?e.TEXTURE_3D:e.TEXTURE_2D_ARRAY),n.bindTexture(c,o.__webglTexture),he(c,a),xe(s.__webglFramebuffer,t,a,e.COLOR_ATTACHMENT0+i,c,0),E(a)&&D(c)}n.unbindTexture()}else{let r=e.TEXTURE_2D;if((t.isWebGL3DRenderTarget||t.isWebGLArrayRenderTarget)&&(r=t.isWebGL3DRenderTarget?e.TEXTURE_3D:e.TEXTURE_2D_ARRAY),n.bindTexture(r,c.__webglTexture),he(r,i),i.mipmaps&&i.mipmaps.length>0)for(let n=0;n<i.mipmaps.length;n++)xe(s.__webglFramebuffer[n],t,i,e.COLOR_ATTACHMENT0,r,n);else xe(s.__webglFramebuffer,t,i,e.COLOR_ATTACHMENT0,r,0);E(i)&&D(r),n.unbindTexture()}t.depthBuffer&&we(t)}function De(e){let t=e.textures;for(let i=0,a=t.length;i<a;i++){let a=t[i];if(E(a)){let t=O(e),i=r.get(a).__webglTexture;n.bindTexture(t,i),D(t),n.unbindTexture()}}}let Oe=[],ke=[];function Ae(t){if(t.samples>0){if(Me(t)===!1){let i=t.textures,a=t.width,o=t.height,s=e.COLOR_BUFFER_BIT,l=t.stencilBuffer?e.DEPTH_STENCIL_ATTACHMENT:e.DEPTH_ATTACHMENT,u=r.get(t),d=i.length>1;if(d)for(let t=0;t<i.length;t++)n.bindFramebuffer(e.FRAMEBUFFER,u.__webglMultisampledFramebuffer),e.framebufferRenderbuffer(e.FRAMEBUFFER,e.COLOR_ATTACHMENT0+t,e.RENDERBUFFER,null),n.bindFramebuffer(e.FRAMEBUFFER,u.__webglFramebuffer),e.framebufferTexture2D(e.DRAW_FRAMEBUFFER,e.COLOR_ATTACHMENT0+t,e.TEXTURE_2D,null,0);n.bindFramebuffer(e.READ_FRAMEBUFFER,u.__webglMultisampledFramebuffer);let f=t.texture.mipmaps;f&&f.length>0?n.bindFramebuffer(e.DRAW_FRAMEBUFFER,u.__webglFramebuffer[0]):n.bindFramebuffer(e.DRAW_FRAMEBUFFER,u.__webglFramebuffer);for(let n=0;n<i.length;n++){if(t.resolveDepthBuffer&&(t.depthBuffer&&(s|=e.DEPTH_BUFFER_BIT),t.stencilBuffer&&t.resolveStencilBuffer&&(s|=e.STENCIL_BUFFER_BIT)),d){e.framebufferRenderbuffer(e.READ_FRAMEBUFFER,e.COLOR_ATTACHMENT0,e.RENDERBUFFER,u.__webglColorRenderbuffer[n]);let t=r.get(i[n]).__webglTexture;e.framebufferTexture2D(e.DRAW_FRAMEBUFFER,e.COLOR_ATTACHMENT0,e.TEXTURE_2D,t,0)}e.blitFramebuffer(0,0,a,o,0,0,a,o,s,e.NEAREST),c===!0&&(Oe.length=0,ke.length=0,Oe.push(e.COLOR_ATTACHMENT0+n),t.depthBuffer&&t.storeMultisampledDepthBuffer===!1&&(Oe.push(l),ke.push(l),e.invalidateFramebuffer(e.DRAW_FRAMEBUFFER,ke)),e.invalidateFramebuffer(e.READ_FRAMEBUFFER,Oe))}if(n.bindFramebuffer(e.READ_FRAMEBUFFER,null),n.bindFramebuffer(e.DRAW_FRAMEBUFFER,null),d)for(let t=0;t<i.length;t++){n.bindFramebuffer(e.FRAMEBUFFER,u.__webglMultisampledFramebuffer),e.framebufferRenderbuffer(e.FRAMEBUFFER,e.COLOR_ATTACHMENT0+t,e.RENDERBUFFER,u.__webglColorRenderbuffer[t]);let a=r.get(i[t]).__webglTexture;n.bindFramebuffer(e.FRAMEBUFFER,u.__webglFramebuffer),e.framebufferTexture2D(e.DRAW_FRAMEBUFFER,e.COLOR_ATTACHMENT0+t,e.TEXTURE_2D,a,0)}n.bindFramebuffer(e.DRAW_FRAMEBUFFER,u.__webglMultisampledFramebuffer)}else if(t.depthBuffer&&t.storeMultisampledDepthBuffer===!1&&c){let n=t.stencilBuffer?e.DEPTH_STENCIL_ATTACHMENT:e.DEPTH_ATTACHMENT;e.invalidateFramebuffer(e.DRAW_FRAMEBUFFER,[n])}}}function je(e){return Math.min(i.maxSamples,e.samples)}function Me(e){let n=r.get(e);return e.samples>0&&t.has(`WEBGL_multisampled_render_to_texture`)===!0&&n.__useRenderToTexture!==!1}function I(e){let t=o.render.frame;u.get(e)!==t&&(u.set(e,t),e.update())}function Ne(e,t){let n=e.colorSpace,r=e.format,i=e.type;return e.isCompressedTexture===!0||e.isVideoTexture===!0||n!==`srgb-linear`&&n!==``&&(q.getTransfer(n)===`srgb`?(r!==1023||i!==1009)&&V(`WebGLTextures: sRGB encoded textures have to use RGBAFormat and UnsignedByteType.`):H(`WebGLTextures: Unsupported texture color space:`,n)),t}function L(e){return typeof HTMLImageElement<`u`&&e instanceof HTMLImageElement?(l.width=e.naturalWidth||e.width,l.height=e.naturalHeight||e.height):typeof VideoFrame<`u`&&e instanceof VideoFrame?(l.width=e.displayWidth,l.height=e.displayHeight):(l.width=e.width,l.height=e.height),l}this.allocateTextureUnit=se,this.resetTextureUnits=ie,this.getTextureUnits=ae,this.setTextureUnits=oe,this.setTexture2D=F,this.setTexture2DArray=le,this.setTexture3D=ue,this.setTextureCube=de,this.rebindTextures=Te,this.setupRenderTarget=Ee,this.updateRenderTargetMipmap=De,this.updateMultisampleRenderTarget=Ae,this.setupDepthRenderbuffer=we,this.setupFrameBufferTexture=xe,this.useMultisampledRTT=Me,this.isReversedDepthBuffer=function(){return n.buffers.depth.getReversed()}}function _l(e,t){function n(n,r=``){let i,a=q.getTransfer(r);if(n===1009)return e.UNSIGNED_BYTE;if(n===1017)return e.UNSIGNED_SHORT_4_4_4_4;if(n===1018)return e.UNSIGNED_SHORT_5_5_5_1;if(n===35902)return e.UNSIGNED_INT_5_9_9_9_REV;if(n===35899)return e.UNSIGNED_INT_10F_11F_11F_REV;if(n===1010)return e.BYTE;if(n===1011)return e.SHORT;if(n===1012)return e.UNSIGNED_SHORT;if(n===1013)return e.INT;if(n===1014)return e.UNSIGNED_INT;if(n===1015)return e.FLOAT;if(n===1016)return e.HALF_FLOAT;if(n===1021)return e.ALPHA;if(n===1022)return e.RGB;if(n===1023)return e.RGBA;if(n===1026)return e.DEPTH_COMPONENT;if(n===1027)return e.DEPTH_STENCIL;if(n===1028)return e.RED;if(n===1029)return e.RED_INTEGER;if(n===1030)return e.RG;if(n===1031)return e.RG_INTEGER;if(n===1033)return e.RGBA_INTEGER;if(n===33776||n===33777||n===33778||n===33779){if(a===`srgb`){if(i=t.get(`WEBGL_compressed_texture_s3tc_srgb`),i!==null){if(n===33776)return i.COMPRESSED_SRGB_S3TC_DXT1_EXT;if(n===33777)return i.COMPRESSED_SRGB_ALPHA_S3TC_DXT1_EXT;if(n===33778)return i.COMPRESSED_SRGB_ALPHA_S3TC_DXT3_EXT;if(n===33779)return i.COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT}else return null}else if(i=t.get(`WEBGL_compressed_texture_s3tc`),i!==null){if(n===33776)return i.COMPRESSED_RGB_S3TC_DXT1_EXT;if(n===33777)return i.COMPRESSED_RGBA_S3TC_DXT1_EXT;if(n===33778)return i.COMPRESSED_RGBA_S3TC_DXT3_EXT;if(n===33779)return i.COMPRESSED_RGBA_S3TC_DXT5_EXT}else return null}if(n===35840||n===35841||n===35842||n===35843){if(i=t.get(`WEBGL_compressed_texture_pvrtc`),i!==null){if(n===35840)return i.COMPRESSED_RGB_PVRTC_4BPPV1_IMG;if(n===35841)return i.COMPRESSED_RGB_PVRTC_2BPPV1_IMG;if(n===35842)return i.COMPRESSED_RGBA_PVRTC_4BPPV1_IMG;if(n===35843)return i.COMPRESSED_RGBA_PVRTC_2BPPV1_IMG}else return null}if(n===36196||n===37492||n===37496||n===37488||n===37489||n===37490||n===37491){if(i=t.get(`WEBGL_compressed_texture_etc`),i!==null){if(n===36196||n===37492)return a===`srgb`?i.COMPRESSED_SRGB8_ETC2:i.COMPRESSED_RGB8_ETC2;if(n===37496)return a===`srgb`?i.COMPRESSED_SRGB8_ALPHA8_ETC2_EAC:i.COMPRESSED_RGBA8_ETC2_EAC;if(n===37488)return i.COMPRESSED_R11_EAC;if(n===37489)return i.COMPRESSED_SIGNED_R11_EAC;if(n===37490)return i.COMPRESSED_RG11_EAC;if(n===37491)return i.COMPRESSED_SIGNED_RG11_EAC}else return null}if(n===37808||n===37809||n===37810||n===37811||n===37812||n===37813||n===37814||n===37815||n===37816||n===37817||n===37818||n===37819||n===37820||n===37821){if(i=t.get(`WEBGL_compressed_texture_astc`),i!==null){if(n===37808)return a===`srgb`?i.COMPRESSED_SRGB8_ALPHA8_ASTC_4x4_KHR:i.COMPRESSED_RGBA_ASTC_4x4_KHR;if(n===37809)return a===`srgb`?i.COMPRESSED_SRGB8_ALPHA8_ASTC_5x4_KHR:i.COMPRESSED_RGBA_ASTC_5x4_KHR;if(n===37810)return a===`srgb`?i.COMPRESSED_SRGB8_ALPHA8_ASTC_5x5_KHR:i.COMPRESSED_RGBA_ASTC_5x5_KHR;if(n===37811)return a===`srgb`?i.COMPRESSED_SRGB8_ALPHA8_ASTC_6x5_KHR:i.COMPRESSED_RGBA_ASTC_6x5_KHR;if(n===37812)return a===`srgb`?i.COMPRESSED_SRGB8_ALPHA8_ASTC_6x6_KHR:i.COMPRESSED_RGBA_ASTC_6x6_KHR;if(n===37813)return a===`srgb`?i.COMPRESSED_SRGB8_ALPHA8_ASTC_8x5_KHR:i.COMPRESSED_RGBA_ASTC_8x5_KHR;if(n===37814)return a===`srgb`?i.COMPRESSED_SRGB8_ALPHA8_ASTC_8x6_KHR:i.COMPRESSED_RGBA_ASTC_8x6_KHR;if(n===37815)return a===`srgb`?i.COMPRESSED_SRGB8_ALPHA8_ASTC_8x8_KHR:i.COMPRESSED_RGBA_ASTC_8x8_KHR;if(n===37816)return a===`srgb`?i.COMPRESSED_SRGB8_ALPHA8_ASTC_10x5_KHR:i.COMPRESSED_RGBA_ASTC_10x5_KHR;if(n===37817)return a===`srgb`?i.COMPRESSED_SRGB8_ALPHA8_ASTC_10x6_KHR:i.COMPRESSED_RGBA_ASTC_10x6_KHR;if(n===37818)return a===`srgb`?i.COMPRESSED_SRGB8_ALPHA8_ASTC_10x8_KHR:i.COMPRESSED_RGBA_ASTC_10x8_KHR;if(n===37819)return a===`srgb`?i.COMPRESSED_SRGB8_ALPHA8_ASTC_10x10_KHR:i.COMPRESSED_RGBA_ASTC_10x10_KHR;if(n===37820)return a===`srgb`?i.COMPRESSED_SRGB8_ALPHA8_ASTC_12x10_KHR:i.COMPRESSED_RGBA_ASTC_12x10_KHR;if(n===37821)return a===`srgb`?i.COMPRESSED_SRGB8_ALPHA8_ASTC_12x12_KHR:i.COMPRESSED_RGBA_ASTC_12x12_KHR}else return null}if(n===36492||n===36494||n===36495){if(i=t.get(`EXT_texture_compression_bptc`),i!==null){if(n===36492)return a===`srgb`?i.COMPRESSED_SRGB_ALPHA_BPTC_UNORM_EXT:i.COMPRESSED_RGBA_BPTC_UNORM_EXT;if(n===36494)return i.COMPRESSED_RGB_BPTC_SIGNED_FLOAT_EXT;if(n===36495)return i.COMPRESSED_RGB_BPTC_UNSIGNED_FLOAT_EXT}else return null}if(n===36283||n===36284||n===36285||n===36286){if(i=t.get(`EXT_texture_compression_rgtc`),i!==null){if(n===36283)return i.COMPRESSED_RED_RGTC1_EXT;if(n===36284)return i.COMPRESSED_SIGNED_RED_RGTC1_EXT;if(n===36285)return i.COMPRESSED_RED_GREEN_RGTC2_EXT;if(n===36286)return i.COMPRESSED_SIGNED_RED_GREEN_RGTC2_EXT}else return null}return n===1020?e.UNSIGNED_INT_24_8:e[n]===void 0?null:e[n]}return{convert:n}}var vl=`
void main() {

	gl_Position = vec4( position, 1.0 );

}`,yl=`
uniform sampler2DArray depthColor;
uniform float depthWidth;
uniform float depthHeight;

void main() {

	vec2 coord = vec2( gl_FragCoord.x / depthWidth, gl_FragCoord.y / depthHeight );

	if ( coord.x >= 1.0 ) {

		gl_FragDepth = texture( depthColor, vec3( coord.x - 1.0, coord.y, 1 ) ).r;

	} else {

		gl_FragDepth = texture( depthColor, vec3( coord.x, coord.y, 0 ) ).r;

	}

}`,bl=class{constructor(){this.texture=null,this.mesh=null,this.depthNear=0,this.depthFar=0}init(e,t){if(this.texture===null){let n=new Yi(e.texture);(e.depthNear!==t.depthNear||e.depthFar!==t.depthFar)&&(this.depthNear=e.depthNear,this.depthFar=e.depthFar),this.texture=n}}getMesh(e){if(this.texture!==null&&this.mesh===null){let t=e.cameras[0].viewport,n=new fa({vertexShader:vl,fragmentShader:yl,uniforms:{depthColor:{value:this.texture},depthWidth:{value:t.z},depthHeight:{value:t.w}}});this.mesh=new ci(new na(20,20),n)}return this.mesh}reset(){this.texture=null,this.mesh=null}getDepthTexture(){return this.texture}},xl=class extends pt{constructor(e,t){super();let n=this,r=null,i=1,a=null,o=`local-floor`,s=1,c=null,l=null,u=null,d=null,f=null,p=null,m=typeof XRWebGLBinding<`u`,h=new bl,g={},_=t.getContextAttributes(),v=null,y=null,b=[],x=[],S=new W,C=null,T=null,E=new Ua;E.viewport=new rn;let D=new Ua;D.viewport=new rn;let O=[E,D],A=new Ja,j=null,ee=null;this.cameraAutoUpdate=!0,this.enabled=!1,this.isPresenting=!1,this.getController=function(e){let t=b[e];return t===void 0&&(t=new Rn,b[e]=t),t.getTargetRaySpace()},this.getControllerGrip=function(e){let t=b[e];return t===void 0&&(t=new Rn,b[e]=t),t.getGripSpace()},this.getHand=function(e){let t=b[e];return t===void 0&&(t=new Rn,b[e]=t),t.getHandSpace()};function M(e){let t=x.indexOf(e.inputSource);if(t===-1)return;let n=b[t];n!==void 0&&(n.update(e.inputSource,e.frame,c||a),n.dispatchEvent({type:e.type,data:e.inputSource}))}function N(){r.removeEventListener(`select`,M),r.removeEventListener(`selectstart`,M),r.removeEventListener(`selectend`,M),r.removeEventListener(`squeeze`,M),r.removeEventListener(`squeezestart`,M),r.removeEventListener(`squeezeend`,M),r.removeEventListener(`end`,N),r.removeEventListener(`inputsourceschange`,ne);for(let e=0;e<b.length;e++){let t=x[e];t!==null&&(x[e]=null,b[e].disconnect(t))}j=null,ee=null,h.reset();for(let e in g)delete g[e];if(e.setRenderTarget(v),f=null,d=null,u=null,r=null,y=null,de.stop(),n.isPresenting=!1,e.setPixelRatio(C),e.setSize(S.width,S.height,!1),T!==null){let e=T.camera;e.fov=T.fov,e.zoom=T.zoom,e.updateProjectionMatrix(),T=null}n.dispatchEvent({type:`sessionend`})}this.setFramebufferScaleFactor=function(e){i=e,n.isPresenting===!0&&V(`WebXRManager: Cannot change framebuffer scale while presenting.`)},this.setReferenceSpaceType=function(e){o=e,n.isPresenting===!0&&V(`WebXRManager: Cannot change reference space type while presenting.`)},this.getReferenceSpace=function(){return c||a},this.setReferenceSpace=function(e){c=e},this.getBaseLayer=function(){return d===null?f:d},this.getBinding=function(){return u===null&&m&&(u=new XRWebGLBinding(r,t)),u},this.getFrame=function(){return p},this.getSession=function(){return r},this.setSession=async function(l){if(r=l,r!==null){if(v=e.getRenderTarget(),r.addEventListener(`select`,M),r.addEventListener(`selectstart`,M),r.addEventListener(`selectend`,M),r.addEventListener(`squeeze`,M),r.addEventListener(`squeezestart`,M),r.addEventListener(`squeezeend`,M),r.addEventListener(`end`,N),r.addEventListener(`inputsourceschange`,ne),_.xrCompatible!==!0&&await t.makeXRCompatible(),C=e.getPixelRatio(),e.getSize(S),m&&`createProjectionLayer`in XRWebGLBinding.prototype){let n=null,a=null,o=null;_.depth&&(o=_.stencil?t.DEPTH24_STENCIL8:t.DEPTH_COMPONENT24,n=_.stencil?P:oe,a=_.stencil?te:k);let s={colorFormat:t.RGBA8,depthFormat:o,scaleFactor:i};u=this.getBinding(),d=u.createProjectionLayer(s),r.updateRenderState({layers:[d]}),e.setPixelRatio(1),e.setSize(d.textureWidth,d.textureHeight,!1),y=new on(d.textureWidth,d.textureHeight,{format:ae,type:w,depthTexture:new qi(d.textureWidth,d.textureHeight,a,void 0,void 0,void 0,void 0,void 0,void 0,n),stencilBuffer:_.stencil,colorSpace:e.outputColorSpace,samples:_.antialias?4:0,resolveDepthBuffer:d.ignoreDepthValues===!1,resolveStencilBuffer:d.ignoreDepthValues===!1,storeMultisampledDepthBuffer:d.ignoreDepthValues===!1,storeMultisampledStencilBuffer:d.ignoreDepthValues===!1})}else{let n={antialias:_.antialias,alpha:!0,depth:_.depth,stencil:_.stencil,framebufferScaleFactor:i};f=new XRWebGLLayer(r,t,n),r.updateRenderState({baseLayer:f}),e.setPixelRatio(1),e.setSize(f.framebufferWidth,f.framebufferHeight,!1),y=new on(f.framebufferWidth,f.framebufferHeight,{format:ae,type:w,colorSpace:e.outputColorSpace,stencilBuffer:_.stencil,resolveDepthBuffer:f.ignoreDepthValues===!1,resolveStencilBuffer:f.ignoreDepthValues===!1,storeMultisampledDepthBuffer:f.ignoreDepthValues===!1,storeMultisampledStencilBuffer:f.ignoreDepthValues===!1})}y.isXRRenderTarget=!0,this.setFoveation(s),c=null,a=await r.requestReferenceSpace(o),de.setContext(r),de.start(),n.isPresenting=!0,n.dispatchEvent({type:`sessionstart`})}},this.getEnvironmentBlendMode=function(){if(r!==null)return r.environmentBlendMode},this.getDepthTexture=function(){return h.getDepthTexture()};function ne(e){for(let t=0;t<e.removed.length;t++){let n=e.removed[t],r=x.indexOf(n);r>=0&&(x[r]=null,b[r].disconnect(n))}for(let t=0;t<e.added.length;t++){let n=e.added[t],r=x.indexOf(n);if(r===-1){for(let e=0;e<b.length;e++)if(e>=x.length){x.push(n),r=e;break}else if(x[e]===null){x[e]=n,r=e;break}if(r===-1)break}let i=b[r];i&&i.connect(n)}}let re=new G,ie=new G;function se(e,t,n){re.setFromMatrixPosition(t.matrixWorld),ie.setFromMatrixPosition(n.matrixWorld);let r=re.distanceTo(ie),i=t.projectionMatrix.elements,a=n.projectionMatrix.elements,o=i[14]/(i[10]-1),s=i[14]/(i[10]+1),c=(i[9]+1)/i[5],l=(i[9]-1)/i[5],u=(i[8]-1)/i[0],d=(a[8]+1)/a[0],f=o*u,p=o*d,m=r/(-u+d),h=m*-u;if(t.matrixWorld.decompose(e.position,e.quaternion,e.scale),e.translateX(h),e.translateZ(m),e.matrixWorld.compose(e.position,e.quaternion,e.scale),e.matrixWorldInverse.copy(e.matrixWorld).invert(),i[10]===-1)e.projectionMatrix.copy(t.projectionMatrix),e.projectionMatrixInverse.copy(t.projectionMatrixInverse);else{let t=o+m,n=s+m,i=f-h,a=p+(r-h),u=c*s/n*t,d=l*s/n*t;e.projectionMatrix.makePerspective(i,a,u,d,t,n),e.projectionMatrixInverse.copy(e.projectionMatrix).invert()}}function ce(e,t){t===null?e.matrixWorld.copy(e.matrix):e.matrixWorld.multiplyMatrices(t.matrixWorld,e.matrix),e.matrixWorldInverse.copy(e.matrixWorld).invert()}this.updateCamera=function(e){if(r===null)return;let t=e.near,n=e.far;h.texture!==null&&(h.depthNear>0&&(t=h.depthNear),h.depthFar>0&&(n=h.depthFar)),A.near=D.near=E.near=t,A.far=D.far=E.far=n,(j!==A.near||ee!==A.far)&&(r.updateRenderState({depthNear:A.near,depthFar:A.far}),j=A.near,ee=A.far),A.layers.mask=e.layers.mask|6,E.layers.mask=A.layers.mask&-5,D.layers.mask=A.layers.mask&-3;let i=e.parent,a=A.cameras;ce(A,i);for(let e=0;e<a.length;e++)ce(a[e],i);a.length===2?se(A,E,D):A.projectionMatrix.copy(E.projectionMatrix),T===null&&e.isPerspectiveCamera&&(T={camera:e,fov:e.fov,zoom:e.zoom}),F(e,A,i)};function F(e,t,n){n===null?e.matrix.copy(t.matrixWorld):(e.matrix.copy(n.matrixWorld),e.matrix.invert(),e.matrix.multiply(t.matrixWorld)),e.matrix.decompose(e.position,e.quaternion,e.scale),e.updateMatrixWorld(!0),e.projectionMatrix.copy(t.projectionMatrix),e.projectionMatrixInverse.copy(t.projectionMatrixInverse),e.isPerspectiveCamera&&(e.fov=_t*2*Math.atan(1/e.projectionMatrix.elements[5]),e.zoom=1)}this.getCamera=function(){return A},this.getFoveation=function(){if(d!==null||f!==null)return s},this.setFoveation=function(e){s=e,d!==null&&(d.fixedFoveation=e),f!==null&&f.fixedFoveation!==void 0&&(f.fixedFoveation=e)},this.hasDepthSensing=function(){return h.texture!==null},this.getDepthSensingMesh=function(){return h.getMesh(A)},this.getCameraTexture=function(e){return g[e]};let le=null;function ue(t,i){if(l=i.getViewerPose(c||a),p=i,l!==null){let t=l.views;f!==null&&(e.setRenderTargetFramebuffer(y,f.framebuffer),e.setRenderTarget(y));let i=!1;t.length!==A.cameras.length&&(A.cameras.length=0,i=!0);for(let n=0;n<t.length;n++){let r=t[n],a=null;if(f!==null)a=f.getViewport(r);else{let t=u.getViewSubImage(d,r);a=t.viewport,n===0&&(e.setRenderTargetTextures(y,t.colorTexture,t.depthStencilTexture),e.setRenderTarget(y))}let o=O[n];o===void 0&&(o=new Ua,o.layers.enable(n),o.viewport=new rn,O[n]=o),o.matrix.fromArray(r.transform.matrix),o.matrix.decompose(o.position,o.quaternion,o.scale),o.projectionMatrix.fromArray(r.projectionMatrix),o.projectionMatrixInverse.copy(o.projectionMatrix).invert(),o.viewport.set(a.x,a.y,a.width,a.height),n===0&&(A.matrix.copy(o.matrix),A.matrix.decompose(A.position,A.quaternion,A.scale)),i===!0&&A.cameras.push(o)}let a=r.enabledFeatures;if(a&&a.includes(`depth-sensing`)&&r.depthUsage==`gpu-optimized`&&m){u=n.getBinding();let e=u.getDepthInformation(t[0]);e&&e.isValid&&e.texture&&h.init(e,r.renderState)}if(a&&a.includes(`camera-access`)&&m){e.state.unbindTexture(),u=n.getBinding();for(let e=0;e<t.length;e++){let n=t[e].camera;if(n){let e=g[n];e||(e=new Yi,g[n]=e);let t=u.getCameraImage(n);e.sourceTexture=t}}}}for(let e=0;e<b.length;e++){let t=x[e],n=b[e];t!==null&&n!==void 0&&n.update(t,i,c||a)}le&&le(t,i),i.detectedPlanes&&n.dispatchEvent({type:`planesdetected`,data:i}),p=null}let de=new po;de.setAnimationLoop(ue),this.setAnimationLoop=function(e){le=e},this.dispose=function(){}}},Sl=new J,Cl=new K;Cl.set(-1,0,0,0,1,0,0,0,1);function wl(e,t){function n(e,t){e.matrixAutoUpdate===!0&&e.updateMatrix(),t.value.copy(e.matrix)}function r(t,n){n.color.getRGB(t.fogColor.value,ca(e)),n.isFog?(t.fogNear.value=n.near,t.fogFar.value=n.far):n.isFogExp2&&(t.fogDensity.value=n.density)}function i(e,t,n,r,i){t.isNodeMaterial?t.uniformsNeedUpdate=!1:t.isMeshBasicMaterial?a(e,t):t.isMeshLambertMaterial?(a(e,t),t.envMap&&(e.envMapIntensity.value=t.envMapIntensity)):t.isMeshToonMaterial?(a(e,t),d(e,t)):t.isMeshPhongMaterial?(a(e,t),u(e,t),t.envMap&&(e.envMapIntensity.value=t.envMapIntensity)):t.isMeshStandardMaterial?(a(e,t),f(e,t),t.isMeshPhysicalMaterial&&p(e,t,i)):t.isMeshMatcapMaterial?(a(e,t),m(e,t)):t.isMeshDepthMaterial?a(e,t):t.isMeshDistanceMaterial?(a(e,t),h(e,t)):t.isMeshNormalMaterial?a(e,t):t.isLineBasicMaterial?(o(e,t),t.isLineDashedMaterial&&s(e,t)):t.isPointsMaterial?c(e,t,n,r):t.isSpriteMaterial?l(e,t):t.isShadowMaterial?(e.color.value.copy(t.color),e.opacity.value=t.opacity):t.isShaderMaterial&&(t.uniformsNeedUpdate=!1)}function a(e,r){e.opacity.value=r.opacity,r.color&&e.diffuse.value.copy(r.color),r.emissive&&e.emissive.value.copy(r.emissive).multiplyScalar(r.emissiveIntensity),r.map&&(e.map.value=r.map,n(r.map,e.mapTransform)),r.alphaMap&&(e.alphaMap.value=r.alphaMap,n(r.alphaMap,e.alphaMapTransform)),r.bumpMap&&(e.bumpMap.value=r.bumpMap,n(r.bumpMap,e.bumpMapTransform),e.bumpScale.value=r.bumpScale,r.side===1&&(e.bumpScale.value*=-1)),r.normalMap&&(e.normalMap.value=r.normalMap,n(r.normalMap,e.normalMapTransform),e.normalScale.value.copy(r.normalScale),r.side===1&&e.normalScale.value.negate()),r.displacementMap&&(e.displacementMap.value=r.displacementMap,n(r.displacementMap,e.displacementMapTransform),e.displacementScale.value=r.displacementScale,e.displacementBias.value=r.displacementBias),r.emissiveMap&&(e.emissiveMap.value=r.emissiveMap,n(r.emissiveMap,e.emissiveMapTransform)),r.specularMap&&(e.specularMap.value=r.specularMap,n(r.specularMap,e.specularMapTransform)),r.alphaTest>0&&(e.alphaTest.value=r.alphaTest);let i=t.get(r),a=i.envMap,o=i.envMapRotation;a&&(e.envMap.value=a,e.envMapRotation.value.setFromMatrix4(Sl.makeRotationFromEuler(o)).transpose(),a.isCubeTexture&&a.isRenderTargetTexture===!1&&e.envMapRotation.value.premultiply(Cl),e.reflectivity.value=r.reflectivity,e.ior.value=r.ior,e.refractionRatio.value=r.refractionRatio),r.lightMap&&(e.lightMap.value=r.lightMap,e.lightMapIntensity.value=r.lightMapIntensity,n(r.lightMap,e.lightMapTransform)),r.aoMap&&(e.aoMap.value=r.aoMap,e.aoMapIntensity.value=r.aoMapIntensity,n(r.aoMap,e.aoMapTransform))}function o(e,t){e.diffuse.value.copy(t.color),e.opacity.value=t.opacity,t.map&&(e.map.value=t.map,n(t.map,e.mapTransform))}function s(e,t){e.dashSize.value=t.dashSize,e.totalSize.value=t.dashSize+t.gapSize,e.scale.value=t.scale}function c(e,t,r,i){e.diffuse.value.copy(t.color),e.opacity.value=t.opacity,e.size.value=t.size*r,e.scale.value=i*.5,t.map&&(e.map.value=t.map,n(t.map,e.uvTransform)),t.alphaMap&&(e.alphaMap.value=t.alphaMap,n(t.alphaMap,e.alphaMapTransform)),t.alphaTest>0&&(e.alphaTest.value=t.alphaTest)}function l(e,t){e.diffuse.value.copy(t.color),e.opacity.value=t.opacity,e.rotation.value=t.rotation,t.map&&(e.map.value=t.map,n(t.map,e.mapTransform)),t.alphaMap&&(e.alphaMap.value=t.alphaMap,n(t.alphaMap,e.alphaMapTransform)),t.alphaTest>0&&(e.alphaTest.value=t.alphaTest)}function u(e,t){e.specular.value.copy(t.specular),e.shininess.value=Math.max(t.shininess,1e-4)}function d(e,t){t.gradientMap&&(e.gradientMap.value=t.gradientMap)}function f(e,t){e.metalness.value=t.metalness,t.metalnessMap&&(e.metalnessMap.value=t.metalnessMap,n(t.metalnessMap,e.metalnessMapTransform)),e.roughness.value=t.roughness,t.roughnessMap&&(e.roughnessMap.value=t.roughnessMap,n(t.roughnessMap,e.roughnessMapTransform)),t.envMap&&(e.envMapIntensity.value=t.envMapIntensity)}function p(e,t,r){e.ior.value=t.ior,t.sheen>0&&(e.sheenColor.value.copy(t.sheenColor).multiplyScalar(t.sheen),e.sheenRoughness.value=t.sheenRoughness,t.sheenColorMap&&(e.sheenColorMap.value=t.sheenColorMap,n(t.sheenColorMap,e.sheenColorMapTransform)),t.sheenRoughnessMap&&(e.sheenRoughnessMap.value=t.sheenRoughnessMap,n(t.sheenRoughnessMap,e.sheenRoughnessMapTransform))),t.clearcoat>0&&(e.clearcoat.value=t.clearcoat,e.clearcoatRoughness.value=t.clearcoatRoughness,t.clearcoatMap&&(e.clearcoatMap.value=t.clearcoatMap,n(t.clearcoatMap,e.clearcoatMapTransform)),t.clearcoatRoughnessMap&&(e.clearcoatRoughnessMap.value=t.clearcoatRoughnessMap,n(t.clearcoatRoughnessMap,e.clearcoatRoughnessMapTransform)),t.clearcoatNormalMap&&(e.clearcoatNormalMap.value=t.clearcoatNormalMap,n(t.clearcoatNormalMap,e.clearcoatNormalMapTransform),e.clearcoatNormalScale.value.copy(t.clearcoatNormalScale),t.side===1&&e.clearcoatNormalScale.value.negate())),t.dispersion>0&&(e.dispersion.value=t.dispersion),t.retroreflectivity>0&&(e.retroreflectivity.value=t.retroreflectivity),t.iridescence>0&&(e.iridescence.value=t.iridescence,e.iridescenceIOR.value=t.iridescenceIOR,e.iridescenceThicknessMinimum.value=t.iridescenceThicknessRange[0],e.iridescenceThicknessMaximum.value=t.iridescenceThicknessRange[1],t.iridescenceMap&&(e.iridescenceMap.value=t.iridescenceMap,n(t.iridescenceMap,e.iridescenceMapTransform)),t.iridescenceThicknessMap&&(e.iridescenceThicknessMap.value=t.iridescenceThicknessMap,n(t.iridescenceThicknessMap,e.iridescenceThicknessMapTransform))),t.transmission>0&&(e.transmission.value=t.transmission,e.transmissionSamplerMap.value=r.texture,e.transmissionSamplerSize.value.set(r.width,r.height),t.transmissionMap&&(e.transmissionMap.value=t.transmissionMap,n(t.transmissionMap,e.transmissionMapTransform)),e.thickness.value=t.thickness,t.thicknessMap&&(e.thicknessMap.value=t.thicknessMap,n(t.thicknessMap,e.thicknessMapTransform)),e.attenuationDistance.value=t.attenuationDistance,e.attenuationColor.value.copy(t.attenuationColor)),t.anisotropy>0&&(e.anisotropyVector.value.set(t.anisotropy*Math.cos(t.anisotropyRotation),t.anisotropy*Math.sin(t.anisotropyRotation)),t.anisotropyMap&&(e.anisotropyMap.value=t.anisotropyMap,n(t.anisotropyMap,e.anisotropyMapTransform))),e.specularIntensity.value=t.specularIntensity,e.specularColor.value.copy(t.specularColor),t.specularColorMap&&(e.specularColorMap.value=t.specularColorMap,n(t.specularColorMap,e.specularColorMapTransform)),t.specularIntensityMap&&(e.specularIntensityMap.value=t.specularIntensityMap,n(t.specularIntensityMap,e.specularIntensityMapTransform))}function m(e,t){t.matcap&&(e.matcap.value=t.matcap)}function h(e,n){let r=t.get(n).light;e.referencePosition.value.setFromMatrixPosition(r.matrixWorld),e.nearDistance.value=r.shadow.camera.near,e.farDistance.value=r.shadow.camera.far}return{refreshFogUniforms:r,refreshMaterialUniforms:i}}function Tl(e,t,n,r){let i={},a={},o=[],s=e.getParameter(e.MAX_UNIFORM_BUFFER_BINDINGS);function c(e,t){let n=t.program;r.uniformBlockBinding(e,n)}function l(e,n){let o=i[e.id];o===void 0&&(g(e),o=u(e),i[e.id]=o,e.addEventListener(`dispose`,v));let s=n.program;r.updateUBOMapping(e,s);let c=t.render.frame;a[e.id]!==c&&(f(e),a[e.id]=c)}function u(t){let n=d();t.__bindingPointIndex=n;let r=e.createBuffer(),i=t.__size,a=t.usage;return e.bindBuffer(e.UNIFORM_BUFFER,r),e.bufferData(e.UNIFORM_BUFFER,i,a),e.bindBuffer(e.UNIFORM_BUFFER,null),e.bindBufferBase(e.UNIFORM_BUFFER,n,r),r}function d(){for(let e=0;e<s;e++)if(o.indexOf(e)===-1)return o.push(e),e;return H(`WebGLRenderer: Maximum number of simultaneously usable uniforms groups reached.`),0}function f(t){let n=i[t.id],r=t.uniforms,a=t.__cache;e.bindBuffer(e.UNIFORM_BUFFER,n);for(let e=0,t=r.length;e<t;e++){let t=r[e];if(Array.isArray(t))for(let n=0,r=t.length;n<r;n++)p(t[n],e,n,a);else p(t,e,0,a)}e.bindBuffer(e.UNIFORM_BUFFER,null)}function p(t,n,r,i){if(h(t,n,r,i)===!0){let n=t.__offset,r=t.value;if(Array.isArray(r)){let e=0;for(let n=0;n<r.length;n++){let i=r[n],a=_(i);m(i,t.__data,e),typeof i!=`number`&&typeof i!=`boolean`&&!i.isMatrix3&&!ArrayBuffer.isView(i)&&(e+=a.storage/Float32Array.BYTES_PER_ELEMENT)}}else m(r,t.__data,0);e.bufferSubData(e.UNIFORM_BUFFER,n,t.__data)}}function m(e,t,n){typeof e==`number`||typeof e==`boolean`?t[0]=e:e.isMatrix3?(t[0]=e.elements[0],t[1]=e.elements[1],t[2]=e.elements[2],t[3]=0,t[4]=e.elements[3],t[5]=e.elements[4],t[6]=e.elements[5],t[7]=0,t[8]=e.elements[6],t[9]=e.elements[7],t[10]=e.elements[8],t[11]=0):ArrayBuffer.isView(e)?t.set(new e.constructor(e.buffer,e.byteOffset,t.length)):e.toArray(t,n)}function h(e,t,n,r){let i=e.value,a=t+`_`+n;if(r[a]===void 0)return r[a]=typeof i==`number`||typeof i==`boolean`?i:ArrayBuffer.isView(i)?i.slice():i.clone(),!0;{let e=r[a];if(typeof i==`number`||typeof i==`boolean`){if(e!==i)return r[a]=i,!0}else if(ArrayBuffer.isView(i))return!0;else if(e.equals(i)===!1)return e.copy(i),!0}return!1}function g(e){let t=e.uniforms,n=0;for(let e=0,r=t.length;e<r;e++){let r=Array.isArray(t[e])?t[e]:[t[e]];for(let e=0,t=r.length;e<t;e++){let t=r[e],i=Array.isArray(t.value)?t.value:[t.value];for(let e=0,r=i.length;e<r;e++){let r=i[e],a=_(r),o=n%16,s=o%a.boundary,c=o+s;n+=s,c!==0&&16-c<a.storage&&(n+=16-c),t.__data=new Float32Array(a.storage/Float32Array.BYTES_PER_ELEMENT),t.__offset=n,n+=a.storage}}}let r=n%16;return r>0&&(n+=16-r),e.__size=n,e.__cache={},this}function _(e){let t={boundary:0,storage:0};return typeof e==`number`||typeof e==`boolean`?(t.boundary=4,t.storage=4):e.isVector2?(t.boundary=8,t.storage=8):e.isVector3||e.isColor?(t.boundary=16,t.storage=12):e.isVector4?(t.boundary=16,t.storage=16):e.isMatrix3?(t.boundary=48,t.storage=48):e.isMatrix4?(t.boundary=64,t.storage=64):e.isTexture?V(`WebGLRenderer: Texture samplers can not be part of an uniforms group.`):ArrayBuffer.isView(e)?(t.boundary=16,t.storage=e.byteLength):V(`WebGLRenderer: Unsupported uniform value type.`,e),t}function v(t){let n=t.target;n.removeEventListener(`dispose`,v);let r=o.indexOf(n.__bindingPointIndex);o.splice(r,1),e.deleteBuffer(i[n.id]),delete i[n.id],delete a[n.id]}function y(){for(let t in i)e.deleteBuffer(i[t]);o=[],i={},a={}}return{bind:c,update:l,dispose:y}}var El=new Uint16Array([12469,15057,12620,14925,13266,14620,13807,14376,14323,13990,14545,13625,14713,13328,14840,12882,14931,12528,14996,12233,15039,11829,15066,11525,15080,11295,15085,10976,15082,10705,15073,10495,13880,14564,13898,14542,13977,14430,14158,14124,14393,13732,14556,13410,14702,12996,14814,12596,14891,12291,14937,11834,14957,11489,14958,11194,14943,10803,14921,10506,14893,10278,14858,9960,14484,14039,14487,14025,14499,13941,14524,13740,14574,13468,14654,13106,14743,12678,14818,12344,14867,11893,14889,11509,14893,11180,14881,10751,14852,10428,14812,10128,14765,9754,14712,9466,14764,13480,14764,13475,14766,13440,14766,13347,14769,13070,14786,12713,14816,12387,14844,11957,14860,11549,14868,11215,14855,10751,14825,10403,14782,10044,14729,9651,14666,9352,14599,9029,14967,12835,14966,12831,14963,12804,14954,12723,14936,12564,14917,12347,14900,11958,14886,11569,14878,11247,14859,10765,14828,10401,14784,10011,14727,9600,14660,9289,14586,8893,14508,8533,15111,12234,15110,12234,15104,12216,15092,12156,15067,12010,15028,11776,14981,11500,14942,11205,14902,10752,14861,10393,14812,9991,14752,9570,14682,9252,14603,8808,14519,8445,14431,8145,15209,11449,15208,11451,15202,11451,15190,11438,15163,11384,15117,11274,15055,10979,14994,10648,14932,10343,14871,9936,14803,9532,14729,9218,14645,8742,14556,8381,14461,8020,14365,7603,15273,10603,15272,10607,15267,10619,15256,10631,15231,10614,15182,10535,15118,10389,15042,10167,14963,9787,14883,9447,14800,9115,14710,8665,14615,8318,14514,7911,14411,7507,14279,7198,15314,9675,15313,9683,15309,9712,15298,9759,15277,9797,15229,9773,15166,9668,15084,9487,14995,9274,14898,8910,14800,8539,14697,8234,14590,7790,14479,7409,14367,7067,14178,6621,15337,8619,15337,8631,15333,8677,15325,8769,15305,8871,15264,8940,15202,8909,15119,8775,15022,8565,14916,8328,14804,8009,14688,7614,14569,7287,14448,6888,14321,6483,14088,6171,15350,7402,15350,7419,15347,7480,15340,7613,15322,7804,15287,7973,15229,8057,15148,8012,15046,7846,14933,7611,14810,7357,14682,7069,14552,6656,14421,6316,14251,5948,14007,5528,15356,5942,15356,5977,15353,6119,15348,6294,15332,6551,15302,6824,15249,7044,15171,7122,15070,7050,14949,6861,14818,6611,14679,6349,14538,6067,14398,5651,14189,5311,13935,4958,15359,4123,15359,4153,15356,4296,15353,4646,15338,5160,15311,5508,15263,5829,15188,6042,15088,6094,14966,6001,14826,5796,14678,5543,14527,5287,14377,4985,14133,4586,13869,4257,15360,1563,15360,1642,15358,2076,15354,2636,15341,3350,15317,4019,15273,4429,15203,4732,15105,4911,14981,4932,14836,4818,14679,4621,14517,4386,14359,4156,14083,3795,13808,3437,15360,122,15360,137,15358,285,15355,636,15344,1274,15322,2177,15281,2765,15215,3223,15120,3451,14995,3569,14846,3567,14681,3466,14511,3305,14344,3121,14037,2800,13753,2467,15360,0,15360,1,15359,21,15355,89,15346,253,15325,479,15287,796,15225,1148,15133,1492,15008,1749,14856,1882,14685,1886,14506,1783,14324,1608,13996,1398,13702,1183]),Dl=null;function Ol(){return Dl===null&&(Dl=new di(El,16,16,F,j),Dl.name=`DFG_LUT`,Dl.minFilter=x,Dl.magFilter=x,Dl.wrapS=g,Dl.wrapT=g,Dl.generateMipmaps=!1,Dl.needsUpdate=!0),Dl}var kl=class{constructor(e={}){let{canvas:t=ot(),context:n=null,depth:r=!0,stencil:i=!1,alpha:a=!1,antialias:o=!1,premultipliedAlpha:s=!0,preserveDrawingBuffer:c=!1,powerPreference:l=`default`,failIfMajorPerformanceCaveat:u=!1,reversedDepthBuffer:d=!1,outputBufferType:f=w}=e;this.isWebGLRenderer=!0;let p;if(n!==null){if(typeof WebGLRenderingContext<`u`&&n instanceof WebGLRenderingContext)throw Error(`THREE.WebGLRenderer: WebGL 1 is not supported since r163.`);p=n.getContextAttributes().alpha}else p=a;let m=f,h=new Set([ue,le,ce]),g=new Set([w,k,D,te,ee,M]),_=new Uint32Array(4),v=new Int32Array(4),y=new G,b=null,x=null,S=[],T=[],E=null;this.domElement=t,this.debug={checkShaderErrors:!0,diagnostics:{keywords:!1},onShaderError:null},this.autoClear=!0,this.autoClearColor=!0,this.autoClearDepth=!0,this.autoClearStencil=!0,this.sortObjects=!0,this.clippingPlanes=[],this.localClippingEnabled=!1,this.toneMapping=0,this.toneMappingExposure=1,this.transmissionResolutionScale=1;let O=this,A=!1,N=null,ne=null,re=null,ie=null;this._outputColorSpace=Xe;let ae=0,oe=0,P=null,se=-1,F=null,de=new rn,fe=new rn,pe=null,me=new Y(0),he=0,ge=t.width,_e=t.height,ve=1,ye=null,be=null,xe=new rn(0,0,ge,_e),Se=new rn(0,0,ge,_e),Ce=!1,we=new wi,Te=!1,Ee=!1,De=new J,Oe=new G,ke=new rn,Ae={background:null,fog:null,environment:null,overrideMaterial:null,isScene:!0},je=!1;function Me(){return P===null?ve:1}let I=n;function Ne(e,n){return t.getContext(e,n)}let L,Pe,R,Fe,z,B,Ie,Le,Re,ze,Be,Ve,He,Ue,We,Ge,Ke,qe,Je,Ye,Ze,Qe,$e;try{let e={alpha:!0,depth:r,stencil:i,antialias:o,premultipliedAlpha:s,preserveDrawingBuffer:c,powerPreference:l,failIfMajorPerformanceCaveat:u};if(`setAttribute`in t&&t.setAttribute(`data-engine`,`three.js r186`),t.addEventListener(`webglcontextlost`,rt,!1),t.addEventListener(`webglcontextrestored`,it,!1),t.addEventListener(`webglcontextcreationerror`,at,!1),I===null){let t=`webgl2`;if(I=Ne(t,e),I===null)throw Ne(t)?Error(`THREE.WebGLRenderer: Error creating WebGL context with your selected attributes.`):Error(`THREE.WebGLRenderer: Error creating WebGL context.`)}et()}catch(e){throw t.removeEventListener(`webglcontextlost`,rt,!1),t.removeEventListener(`webglcontextrestored`,it,!1),t.removeEventListener(`webglcontextcreationerror`,at,!1),H(`WebGLRenderer: `+e.message),e}function et(){L=new qo(I),L.init(),Ze=new _l(I,L),Pe=new So(I,L,e,Ze),R=new hl(I,L),Pe.reversedDepthBuffer&&d&&R.buffers.depth.setReversed(!0),ne=I.createFramebuffer(),re=I.createFramebuffer(),ie=I.createFramebuffer(),Fe=new Xo(I),z=new Yc,B=new gl(I,L,R,z,Pe,Ze,Fe),Ie=new Ko(O),Le=new mo(I),Qe=new bo(I,Le),Re=new Jo(I,Le,Fe,Qe),ze=new Qo(I,Re,Le,Qe,Fe),qe=new Zo(I,Pe,B),We=new Co(z),Be=new Jc(O,Ie,L,Pe,Qe,We),Ve=new wl(O,z),He=new $c,Ue=new ol(L),Ke=new yo(O,Ie,R,ze,p,s),Ge=new ml(O,ze,Pe),$e=new Tl(I,Fe,Pe,R),Je=new xo(I,L,Fe),Ye=new Yo(I,L,Fe),Fe.programs=Be.programs,O.capabilities=Pe,O.extensions=L,O.properties=z,O.renderLists=He,O.shadowMap=Ge,O.state=R,O.info=Fe}m!==1009&&(E=new es(m,t.width,t.height,o,r,i));let tt=new xl(O,I);this.xr=tt,this.getContext=function(){return I},this.getContextAttributes=function(){return I.getContextAttributes()},this.forceContextLoss=function(){let e=L.get(`WEBGL_lose_context`);e&&e.loseContext()},this.forceContextRestore=function(){let e=L.get(`WEBGL_lose_context`);e&&e.restoreContext()},this.getPixelRatio=function(){return ve},this.setPixelRatio=function(e){e!==void 0&&(ve=e,this.setSize(ge,_e,!1))},this.getSize=function(e){return e.set(ge,_e)},this.setSize=function(e,n,r=!0){tt.isPresenting?V(`WebGLRenderer: Can't change size while VR device is presenting.`):(ge=e,_e=n,t.width=Math.floor(e*ve),t.height=Math.floor(n*ve),r===!0&&(t.style.width=e+`px`,t.style.height=n+`px`),E!==null&&E.setSize(t.width,t.height),this.setViewport(0,0,e,n))},this.getDrawingBufferSize=function(e){return e.set(ge*ve,_e*ve).floor()},this.setDrawingBufferSize=function(e,n,r){ge=e,_e=n,ve=r,t.width=Math.floor(e*r),t.height=Math.floor(n*r),this.setViewport(0,0,e,n)},this.setEffects=function(e){if(m===1009)H(`WebGLRenderer: setEffects() requires outputBufferType set to HalfFloatType or FloatType.`);else{if(e){for(let t=0;t<e.length;t++)if(e[t].isOutputPass===!0){V(`WebGLRenderer: OutputPass is not needed in setEffects(). Tone mapping and color space conversion are applied automatically.`);break}}E.setEffects(e||[])}},this.getCurrentViewport=function(e){return e.copy(de)},this.getViewport=function(e){return e.copy(xe)},this.setViewport=function(e,t,n,r){e.isVector4?xe.set(e.x,e.y,e.z,e.w):xe.set(e,t,n,r),R.viewport(de.copy(xe).multiplyScalar(ve).round())},this.getScissor=function(e){return e.copy(Se)},this.setScissor=function(e,t,n,r){e.isVector4?Se.set(e.x,e.y,e.z,e.w):Se.set(e,t,n,r),R.scissor(fe.copy(Se).multiplyScalar(ve).round())},this.getScissorTest=function(){return Ce},this.setScissorTest=function(e){R.setScissorTest(Ce=e)},this.setOpaqueSort=function(e){ye=e},this.setTransparentSort=function(e){be=e},this.getClearColor=function(e){return e.copy(Ke.getClearColor())},this.setClearColor=function(){Ke.setClearColor(...arguments)},this.getClearAlpha=function(){return Ke.getClearAlpha()},this.setClearAlpha=function(){Ke.setClearAlpha(...arguments)},this.clear=function(e=!0,t=!0,n=!0){let r=0;if(e){let e=!1;if(P!==null){let t=P.texture.format;e=h.has(t)}if(e){let e=P.texture.type,t=g.has(e),n=Ke.getClearColor(),r=Ke.getClearAlpha(),i=n.r,a=n.g,o=n.b;t?(_[0]=i,_[1]=a,_[2]=o,_[3]=r,I.clearBufferuiv(I.COLOR,0,_)):(v[0]=i,v[1]=a,v[2]=o,v[3]=r,I.clearBufferiv(I.COLOR,0,v))}else r|=I.COLOR_BUFFER_BIT}t&&(r|=I.DEPTH_BUFFER_BIT,this.state.buffers.depth.setMask(!0)),n&&(r|=I.STENCIL_BUFFER_BIT,this.state.buffers.stencil.setMask(4294967295)),r!==0&&I.clear(r)},this.clearColor=function(){this.clear(!0,!1,!1)},this.clearDepth=function(){this.clear(!1,!0,!1)},this.clearStencil=function(){this.clear(!1,!1,!0)},this.setNodesHandler=function(e){e.setRenderer(this),N=e},this.dispose=function(){t.removeEventListener(`webglcontextlost`,rt,!1),t.removeEventListener(`webglcontextrestored`,it,!1),t.removeEventListener(`webglcontextcreationerror`,at,!1),Ke.dispose(),He.dispose(),Ue.dispose(),z.dispose(),Ie.dispose(),ze.dispose(),Qe.dispose(),$e.dispose(),Be.dispose(),tt.dispose(),tt.removeEventListener(`sessionstart`,ht),tt.removeEventListener(`sessionend`,gt),_t.stop()};function rt(e){e.preventDefault(),ct(`WebGLRenderer: Context Lost.`),A=!0}function it(){ct(`WebGLRenderer: Context Restored.`),A=!1;let e=Fe.autoReset,t=Ge.enabled,n=Ge.autoUpdate,r=Ge.needsUpdate,i=Ge.type;et(),Fe.autoReset=e,Ge.enabled=t,Ge.autoUpdate=n,Ge.needsUpdate=r,Ge.type=i}function at(e){H(`WebGLRenderer: A WebGL context could not be created. Reason: `,e.statusMessage)}function st(e){let t=e.target;t.removeEventListener(`dispose`,st),lt(t)}function lt(e){ut(e),z.remove(e)}function ut(e){let t=z.get(e).programs;t!==void 0&&(t.forEach(function(e){Be.releaseProgram(e)}),e.isShaderMaterial&&Be.releaseShaderCache(e))}this.renderBufferDirect=function(e,t,n,r,i,a){t===null&&(t=Ae);let o=i.isMesh&&i.matrixWorld.determinantAffine()<0,s=Et(e,t,n,r,i);R.setMaterial(r,o);let c=n.index,l=1;if(r.wireframe===!0){if(c=Re.getWireframeAttribute(n),c===void 0)return;l=2}let u=n.drawRange,d=n.attributes.position,f=u.start*l,p=(u.start+u.count)*l;a!==null&&(f=Math.max(f,a.start*l),p=Math.min(p,(a.start+a.count)*l)),c===null?d!=null&&(f=Math.max(f,0),p=Math.min(p,d.count)):(f=Math.max(f,0),p=Math.min(p,c.count));let m=p-f;if(m<0||m===1/0)return;Qe.setup(i,r,s,n,c);let h,g=Je;if(c!==null&&(h=Le.get(c),g=Ye,g.setIndex(h)),i.isMesh)r.wireframe===!0?(R.setLineWidth(r.wireframeLinewidth*Me()),g.setMode(I.LINES)):g.setMode(I.TRIANGLES);else if(i.isLine){let e=r.linewidth;e===void 0&&(e=1),R.setLineWidth(e*Me()),i.isLineSegments?g.setMode(I.LINES):i.isLineLoop?g.setMode(I.LINE_LOOP):g.setMode(I.LINE_STRIP)}else i.isPoints?g.setMode(I.POINTS):i.isSprite&&g.setMode(I.TRIANGLES);if(i.isBatchedMesh){if(L.get(`WEBGL_multi_draw`))g.renderMultiDraw(i._multiDrawStarts,i._multiDrawCounts,i._multiDrawCount);else{let e=i._multiDrawStarts,t=i._multiDrawCounts,n=i._multiDrawCount,a=c?Le.get(c).bytesPerElement:1,o=z.get(r).currentProgram.getUniforms();for(let r=0;r<n;r++)o.setValue(I,`_gl_DrawID`,r),g.render(e[r]/a,t[r])}}else if(i.isInstancedMesh)g.renderInstances(f,m,i.count);else if(n.isInstancedBufferGeometry){let e=n._maxInstanceCount===void 0?1/0:n._maxInstanceCount,t=Math.min(n.instanceCount,e);g.renderInstances(f,m,t)}else g.render(f,m)};function ft(e,t,n,r){N!==null&&e.isNodeMaterial&&N.setObject(r,e),Te===!0&&We.setState(e,n,!1),e.transparent===!0&&e.side===2&&e.forceSinglePass===!1?(e.side=1,e.needsUpdate=!0,St(e,t,r),e.side=0,e.needsUpdate=!0,St(e,t,r),e.side=2):St(e,t,r)}this.compile=function(e,t,n=null){n===null&&(n=e),N!==null&&N.renderStart(e,t,n),x=Ue.get(n),x.init(t),T.push(x),n.traverseVisible(function(e){e.isLight&&e.layers.test(t.layers)&&(x.pushLight(e),e.castShadow&&x.pushShadow(e))}),e!==n&&e.traverseVisible(function(e){e.isLight&&e.layers.test(t.layers)&&(x.pushLight(e),e.castShadow&&x.pushShadow(e))}),x.setupLights(),N!==null&&N.updateLights(x.state.lightsArray),Ee=this.localClippingEnabled,Te=We.init(this.clippingPlanes,Ee),Te===!0&&We.setGlobalState(this.clippingPlanes,t),N!==null&&Ge.render(x.state.shadowsArray,n,t);let r=new Set;return e.traverse(function(e){if(!(e.isMesh||e.isPoints||e.isLine||e.isSprite))return;let i=e.material;if(i){if(Array.isArray(i))for(let a=0;a<i.length;a++){let o=i[a];ft(o,n,t,e),r.add(o)}else ft(i,n,t,e),r.add(i)}}),x=T.pop(),N!==null&&N.renderEnd(),r},this.compileAsync=function(e,t,n=null){let r=this.compile(e,t,n);return new Promise(t=>{function n(){r.forEach(function(e){let t=z.get(e).currentProgram;(t===void 0||t.isReady())&&r.delete(e)}),r.size===0?t(e):setTimeout(n,10)}L.get(`KHR_parallel_shader_compile`)===null?setTimeout(n,10):n()})};let pt=null;function mt(e){pt&&pt(e)}function ht(){_t.stop()}function gt(){_t.start()}let _t=new po;_t.setAnimationLoop(mt),typeof self<`u`&&_t.setContext(self),this.setAnimationLoop=function(e){pt=e,tt.setAnimationLoop(e),e===null?_t.stop():_t.start()},tt.addEventListener(`sessionstart`,ht),tt.addEventListener(`sessionend`,gt),this.render=function(e,t){if(t!==void 0&&t.isCamera!==!0){H(`WebGLRenderer.render: camera is not an instance of THREE.Camera.`);return}if(A===!0)return;N!==null&&N.renderStart(e,t);let n=tt.enabled===!0&&tt.isPresenting===!0,r=E!==null&&(P===null||n)&&E.begin(O,P);if(e.matrixWorldAutoUpdate===!0&&e.updateMatrixWorld(),t.parent===null&&t.matrixWorldAutoUpdate===!0&&t.updateMatrixWorld(),tt.enabled===!0&&tt.isPresenting===!0&&(E===null||E.isCompositing()===!1)&&(tt.cameraAutoUpdate===!0&&tt.updateCamera(t),t=tt.getCamera()),e.isScene===!0&&e.onBeforeRender(O,e,t,P),x=Ue.get(e,T.length),x.init(t),x.state.textureUnits=B.getTextureUnits(),T.push(x),De.multiplyMatrices(t.projectionMatrix,t.matrixWorldInverse),we.setFromProjectionMatrix(De,nt,t.reversedDepth),Ee=this.localClippingEnabled,Te=We.init(this.clippingPlanes,Ee),b=He.get(e,S.length),b.init(),S.push(b),tt.enabled===!0&&tt.isPresenting===!0){let e=O.xr.getDepthSensingMesh();e!==null&&vt(e,t,-1/0,O.sortObjects)}vt(e,t,0,O.sortObjects),b.finish(),N!==null&&N.updateLights(x.state.lightsArray),O.sortObjects===!0&&b.sort(ye,be),je=tt.enabled===!1||tt.isPresenting===!1||tt.hasDepthSensing()===!1,je&&Ke.addToRenderList(b,e),this.info.render.frame++,this.info.autoReset===!0&&this.info.reset(),Te===!0&&We.beginShadows();let i=x.state.shadowsArray;if(Ge.render(i,e,t),Te===!0&&We.endShadows(),(r&&E.hasRenderPass())===!1){let n=b.opaque,r=b.transmissive;if(x.setupLights(),t.isArrayCamera){let i=t.cameras;if(r.length>0)for(let t=0,a=i.length;t<a;t++){let a=i[t];yt(n,r,e,a)}je&&Ke.render(e);for(let t=0,n=i.length;t<n;t++){let n=i[t];U(b,e,n,n.viewport)}}else r.length>0&&yt(n,r,e,t),je&&Ke.render(e),U(b,e,t)}P!==null&&oe===0&&(B.updateMultisampleRenderTarget(P),B.updateRenderTargetMipmap(P)),r&&E.end(O),e.isScene===!0&&e.onAfterRender(O,e,t),Qe.resetDefaultState(),se=-1,F=null,T.pop(),T.length>0?(x=T[T.length-1],B.setTextureUnits(x.state.textureUnits),Te===!0&&We.setGlobalState(O.clippingPlanes,x.state.camera)):x=null,S.pop(),b=S.length>0?S[S.length-1]:null,N!==null&&N.renderEnd()};function vt(e,t,n,r){if(e.visible===!1)return;if(e.layers.test(t.layers)){if(e.isGroup)n=e.renderOrder;else if(e.isLOD)e.autoUpdate===!0&&e.update(t);else if(e.isLightProbeGrid)x.pushLightProbeGrid(e);else if(e.isLight)x.pushLight(e),e.castShadow&&x.pushShadow(e);else if(e.isSprite){if(!e.frustumCulled||e.intersectsFrustum(we)){r&&ke.setFromMatrixPosition(e.matrixWorld).applyMatrix4(De);let i=ze.update(e),a=e.material;a.visible&&b.push(e,i,a,n,ke.z,null,t)}}else if((e.isMesh||e.isLine||e.isPoints)&&(!e.frustumCulled||e.intersectsFrustum(we))){let i=ze.update(e),a=e.material;if(r&&(e.boundingSphere===void 0?(i.boundingSphere===null&&i.computeBoundingSphere(),ke.copy(i.boundingSphere.center)):(e.boundingSphere===null&&e.computeBoundingSphere(),ke.copy(e.boundingSphere.center)),ke.applyMatrix4(e.matrixWorld).applyMatrix4(De)),Array.isArray(a)){let r=i.groups;for(let o=0,s=r.length;o<s;o++){let s=r[o],c=a[s.materialIndex];c&&c.visible&&b.push(e,i,c,n,ke.z,s,t)}}else a.visible&&b.push(e,i,a,n,ke.z,null,t)}}let i=e.children;for(let e=0,a=i.length;e<a;e++)vt(i[e],t,n,r)}function U(e,t,n,r){let{opaque:i,transmissive:a,transparent:o}=e;x.setupLightsView(n),Te===!0&&We.setGlobalState(O.clippingPlanes,n),r&&R.viewport(de.copy(r)),i.length>0&&bt(i,t,n),a.length>0&&bt(a,t,n),o.length>0&&bt(o,t,n),R.buffers.depth.setTest(!0),R.buffers.depth.setMask(!0),R.buffers.color.setMask(!0),R.setPolygonOffset(!1)}function yt(e,t,n,r){if((n.isScene===!0?n.overrideMaterial:null)!==null)return;if(x.state.transmissionRenderTarget[r.id]===void 0){let e=L.has(`EXT_color_buffer_half_float`)||L.has(`EXT_color_buffer_float`);x.state.transmissionRenderTarget[r.id]=new on(1,1,{generateMipmaps:!0,type:e?j:w,minFilter:C,samples:Math.max(4,Pe.samples),stencilBuffer:i,resolveDepthBuffer:!1,resolveStencilBuffer:!1,storeMultisampledDepthBuffer:!1,storeMultisampledStencilBuffer:!1,colorSpace:q.workingColorSpace})}let a=x.state.transmissionRenderTarget[r.id],o=r.viewport||de;a.setSize(o.z*O.transmissionResolutionScale,o.w*O.transmissionResolutionScale);let s=O.getRenderTarget(),c=O.getActiveCubeFace(),l=O.getActiveMipmapLevel();O.setRenderTarget(a),O.getClearColor(me),he=O.getClearAlpha(),he<1&&O.setClearColor(16777215,.5),O.clear(),je&&Ke.render(n);let u=O.toneMapping;O.toneMapping=0;let d=r.viewport;if(r.viewport!==void 0&&(r.viewport=void 0),x.setupLightsView(r),Te===!0&&We.setGlobalState(O.clippingPlanes,r),bt(e,n,r),B.updateMultisampleRenderTarget(a),B.updateRenderTargetMipmap(a),L.has(`WEBGL_multisampled_render_to_texture`)===!1){let e=!1;for(let i=0,a=t.length;i<a;i++){let{object:a,geometry:o,material:s,group:c}=t[i];if(s.side===2&&a.layers.test(r.layers)){let t=s.side;s.side=1,s.needsUpdate=!0,xt(a,n,r,o,s,c),s.side=t,s.needsUpdate=!0,e=!0}}e===!0&&(B.updateMultisampleRenderTarget(a),B.updateRenderTargetMipmap(a))}O.setRenderTarget(s,c,l),O.setClearColor(me,he),d!==void 0&&(r.viewport=d),O.toneMapping=u}function bt(e,t,n){let r=t.isScene===!0?t.overrideMaterial:null;for(let i=0,a=e.length;i<a;i++){let a=e[i],{object:o,geometry:s,group:c}=a,l=a.material;l.allowOverride===!0&&r!==null&&(l=r),o.layers.test(n.layers)&&xt(o,t,n,s,l,c)}}function xt(e,t,n,r,i,a){N!==null&&i.isNodeMaterial&&N.setObject(e,i),e.onBeforeRender(O,t,n,r,i,a),e.modelViewMatrix.multiplyMatrices(n.matrixWorldInverse,e.matrixWorld),e.normalMatrix.getNormalMatrix(e.modelViewMatrix),i.onBeforeRender(O,t,n,r,e,a),i.transparent===!0&&i.side===2&&i.forceSinglePass===!1?(i.side=1,i.needsUpdate=!0,O.renderBufferDirect(n,t,r,i,e,a),i.side=0,i.needsUpdate=!0,O.renderBufferDirect(n,t,r,i,e,a),i.side=2):O.renderBufferDirect(n,t,r,i,e,a),e.onAfterRender(O,t,n,r,i,a)}function St(e,t,n){t.isScene!==!0&&(t=Ae);let r=z.get(e),i=x.state.lights,a=x.state.shadowsArray,o=i.state.version,s=Be.getParameters(e,i.state,a,t,n,x.state.lightProbeGridArray),c=Be.getProgramCacheKey(s),l=r.programs;r.environment=e.isMeshStandardMaterial||e.isMeshLambertMaterial||e.isMeshPhongMaterial?t.environment:null,r.fog=t.fog;let u=e.isMeshStandardMaterial||e.isMeshLambertMaterial&&!e.envMap||e.isMeshPhongMaterial&&!e.envMap;r.envMap=Ie.get(e.envMap||r.environment,u),r.envMapRotation=r.environment!==null&&e.envMap===null?t.environmentRotation:e.envMapRotation,l===void 0&&(e.addEventListener(`dispose`,st),l=new Map,r.programs=l);let d=l.get(c);if(d!==void 0){if(r.currentProgram===d&&r.lightsStateVersion===o)return wt(e,s),d}else s.uniforms=Be.getUniforms(e),N!==null&&e.isNodeMaterial&&N.build(e,n,s),e.onBeforeCompile(s,O),d=Be.acquireProgram(s,c),l.set(c,d),r.uniforms=s.uniforms;let f=r.uniforms;return(!e.isShaderMaterial&&!e.isRawShaderMaterial||e.clipping===!0)&&(f.clippingPlanes=We.uniform),wt(e,s),r.needsLights=Ot(e),r.lightsStateVersion=o,r.needsLights&&(f.ambientLightColor.value=i.state.ambient,f.lightProbe.value=i.state.probe,f.sunLights.value=i.state.sun,f.sunLightShadows.value=i.state.sunShadow,f.directionalLights.value=i.state.directional,f.directionalLightShadows.value=i.state.directionalShadow,f.spotLights.value=i.state.spot,f.spotLightShadows.value=i.state.spotShadow,f.rectAreaLights.value=i.state.rectArea,f.ltc_1.value=i.state.rectAreaLTC1,f.ltc_2.value=i.state.rectAreaLTC2,f.pointLights.value=i.state.point,f.pointLightShadows.value=i.state.pointShadow,f.hemisphereLights.value=i.state.hemi,f.sunShadowMatrix.value=i.state.sunShadowMatrix,f.sunShadowCascade.value=i.state.sunShadowCascade,f.directionalShadowMatrix.value=i.state.directionalShadowMatrix,f.spotLightMatrix.value=i.state.spotLightMatrix,f.spotLightMap.value=i.state.spotLightMap,f.pointShadowMatrix.value=i.state.pointShadowMatrix),r.lightProbeGrid=x.state.lightProbeGridArray.length>0,r.currentProgram=d,r.uniformsList=null,d}function Ct(e){if(e.uniformsList===null){let t=e.currentProgram.getUniforms();e.uniformsList=sc.seqWithValue(t.seq,e.uniforms)}return e.uniformsList}function wt(e,t){let n=z.get(e);n.outputColorSpace=t.outputColorSpace,n.batching=t.batching,n.batchingColor=t.batchingColor,n.instancing=t.instancing,n.instancingColor=t.instancingColor,n.instancingMorph=t.instancingMorph,n.skinning=t.skinning,n.morphTargets=t.morphTargets,n.morphNormals=t.morphNormals,n.morphColors=t.morphColors,n.morphTargetsCount=t.morphTargetsCount,n.numClippingPlanes=t.numClippingPlanes,n.numIntersection=t.numClipIntersection,n.vertexAlphas=t.vertexAlphas,n.vertexTangents=t.vertexTangents,n.toneMapping=t.toneMapping}function Tt(e,t){if(e.length===0)return null;if(e.length===1)return e[0].texture===null?null:e[0];y.setFromMatrixPosition(t.matrixWorld);for(let t=0,n=e.length;t<n;t++){let n=e[t];if(n.texture!==null&&n.boundingBox.containsPoint(y))return n}return null}function Et(e,t,n,r,i){t.isScene!==!0&&(t=Ae),B.resetTextureUnits();let a=t.fog,o=r.isMeshStandardMaterial||r.isMeshLambertMaterial||r.isMeshPhongMaterial?t.environment:null,s=P===null?O.outputColorSpace:P.isXRRenderTarget===!0?P.texture.colorSpace:q.workingColorSpace,c=r.isMeshStandardMaterial||r.isMeshLambertMaterial&&!r.envMap||r.isMeshPhongMaterial&&!r.envMap,l=Ie.get(r.envMap||o,c),u=r.vertexColors===!0&&!!n.attributes.color&&n.attributes.color.itemSize===4,d=!!n.attributes.tangent&&(!!r.normalMap||r.anisotropy>0),f=!!n.morphAttributes.position,p=!!n.morphAttributes.normal,m=!!n.morphAttributes.color,h=0;r.toneMapped&&(P===null||P.isXRRenderTarget===!0)&&(h=O.toneMapping);let g=n.morphAttributes.position||n.morphAttributes.normal||n.morphAttributes.color,_=g===void 0?0:g.length,v=z.get(r),y=x.state.lights;if(Te===!0&&(Ee===!0||e!==F)){let t=e===F&&r.id===se;We.setState(r,e,t)}let b=!1;r.version===v.__version?v.needsLights&&v.lightsStateVersion!==y.state.version?b=!0:v.outputColorSpace===s?i.isBatchedMesh&&v.batching===!1||!i.isBatchedMesh&&v.batching===!0||i.isBatchedMesh&&v.batchingColor===!0&&i._colorsTexture===null||i.isBatchedMesh&&v.batchingColor===!1&&i._colorsTexture!==null||i.isInstancedMesh&&v.instancing===!1||!i.isInstancedMesh&&v.instancing===!0||i.isSkinnedMesh&&v.skinning===!1||!i.isSkinnedMesh&&v.skinning===!0||i.isInstancedMesh&&v.instancingColor===!0&&i.instanceColor===null||i.isInstancedMesh&&v.instancingColor===!1&&i.instanceColor!==null||i.isInstancedMesh&&v.instancingMorph===!0&&i.morphTexture===null||i.isInstancedMesh&&v.instancingMorph===!1&&i.morphTexture!==null?b=!0:v.envMap===l?r.fog===!0&&v.fog!==a||v.numClippingPlanes!==void 0&&(v.numClippingPlanes!==We.numPlanes||v.numIntersection!==We.numIntersection)?b=!0:v.vertexAlphas===u&&v.vertexTangents===d&&v.morphTargets===f&&v.morphNormals===p&&v.morphColors===m&&v.toneMapping===h&&v.morphTargetsCount===_?!!v.lightProbeGrid!=x.state.lightProbeGridArray.length>0&&(b=!0):b=!0:b=!0:b=!0:(b=!0,v.__version=r.version);let S=v.currentProgram;b===!0&&(S=St(r,t,i),N&&r.isNodeMaterial&&N.onUpdateProgram(r,S,v));let C=!1,w=!1,T=!1,E=S.getUniforms(),D=v.uniforms;if(R.useProgram(S.program)&&(C=!0,w=!0,T=!0),r.id!==se&&(se=r.id,w=!0),v.needsLights){let e=Tt(x.state.lightProbeGridArray,i);v.lightProbeGrid!==e&&(v.lightProbeGrid=e,w=!0)}if(C||F!==e){R.buffers.depth.getReversed()&&e.reversedDepth!==!0&&(e._reversedDepth=!0,e.updateProjectionMatrix()),E.setValue(I,`projectionMatrix`,e.projectionMatrix),E.setValue(I,`viewMatrix`,e.matrixWorldInverse);let t=E.map.cameraPosition;t!==void 0&&t.setValue(I,Oe.setFromMatrixPosition(e.matrixWorld)),Pe.logarithmicDepthBuffer&&E.setValue(I,`logDepthBufFC`,2/(Math.log(e.far+1)/Math.LN2)),(r.isMeshPhongMaterial||r.isMeshToonMaterial||r.isMeshLambertMaterial||r.isMeshBasicMaterial||r.isMeshStandardMaterial||r.isShaderMaterial)&&E.setValue(I,`isOrthographic`,e.isOrthographicCamera===!0),F!==e&&(F=e,w=!0,T=!0)}if(v.needsLights&&(y.state.sunShadowMap.length>0&&E.setValue(I,`sunShadowMap`,y.state.sunShadowMap,B),y.state.directionalShadowMap.length>0&&E.setValue(I,`directionalShadowMap`,y.state.directionalShadowMap,B),y.state.spotShadowMap.length>0&&E.setValue(I,`spotShadowMap`,y.state.spotShadowMap,B),y.state.pointShadowMap.length>0&&E.setValue(I,`pointShadowMap`,y.state.pointShadowMap,B)),i.isSkinnedMesh){E.setOptional(I,i,`bindMatrix`),E.setOptional(I,i,`bindMatrixInverse`);let e=i.skeleton;e&&(e.boneTexture===null&&e.computeBoneTexture(),E.setValue(I,`boneTexture`,e.boneTexture,B))}i.isBatchedMesh&&(E.setOptional(I,i,`batchingTexture`),E.setValue(I,`batchingTexture`,i._matricesTexture,B),E.setOptional(I,i,`batchingIdTexture`),E.setValue(I,`batchingIdTexture`,i._indirectTexture,B),E.setOptional(I,i,`batchingColorTexture`),i._colorsTexture!==null&&E.setValue(I,`batchingColorTexture`,i._colorsTexture,B));let k=n.morphAttributes;if((k.position!==void 0||k.normal!==void 0||k.color!==void 0)&&qe.update(i,n,S),(w||v.receiveShadow!==i.receiveShadow)&&(v.receiveShadow=i.receiveShadow,E.setValue(I,`receiveShadow`,i.receiveShadow)),(r.isMeshStandardMaterial||r.isMeshLambertMaterial||r.isMeshPhongMaterial)&&r.envMap===null&&t.environment!==null&&(D.envMapIntensity.value=t.environmentIntensity),D.dfgLUT!==void 0&&(D.dfgLUT.value=Ol()),w){if(E.setValue(I,`toneMappingExposure`,O.toneMappingExposure),v.needsLights&&Dt(D,T),a&&r.fog===!0&&Ve.refreshFogUniforms(D,a),Ve.refreshMaterialUniforms(D,r,ve,_e,x.state.transmissionRenderTarget[e.id]),v.needsLights&&v.lightProbeGrid){let e=v.lightProbeGrid;D.probesSH.value=e.texture,D.probesMin.value.copy(e.boundingBox.min),D.probesMax.value.copy(e.boundingBox.max),D.probesResolution.value.copy(e.resolution)}sc.upload(I,Ct(v),D,B)}if(r.isShaderMaterial&&r.uniformsNeedUpdate===!0&&(sc.upload(I,Ct(v),D,B),r.uniformsNeedUpdate=!1),r.isSpriteMaterial&&E.setValue(I,`center`,i.center),E.setValue(I,`modelViewMatrix`,i.modelViewMatrix),E.setValue(I,`normalMatrix`,i.normalMatrix),E.setValue(I,`modelMatrix`,i.matrixWorld),r.uniformsGroups!==void 0){let e=r.uniformsGroups;for(let t=0,n=e.length;t<n;t++){let n=e[t];$e.update(n,S),$e.bind(n,S)}}return S}function Dt(e,t){e.ambientLightColor.needsUpdate=t,e.lightProbe.needsUpdate=t,e.sunLights.needsUpdate=t,e.sunLightShadows.needsUpdate=t,e.directionalLights.needsUpdate=t,e.directionalLightShadows.needsUpdate=t,e.pointLights.needsUpdate=t,e.pointLightShadows.needsUpdate=t,e.spotLights.needsUpdate=t,e.spotLightShadows.needsUpdate=t,e.rectAreaLights.needsUpdate=t,e.hemisphereLights.needsUpdate=t}function Ot(e){return e.isMeshLambertMaterial||e.isMeshToonMaterial||e.isMeshPhongMaterial||e.isMeshStandardMaterial||e.isShadowMaterial||e.isShaderMaterial&&e.lights===!0}this.getActiveCubeFace=function(){return ae},this.getActiveMipmapLevel=function(){return oe},this.getRenderTarget=function(){return P},this.setRenderTargetTextures=function(e,t,n){let r=z.get(e);r.__autoAllocateDepthBuffer=e.resolveDepthBuffer===!1,r.__autoAllocateDepthBuffer===!1&&(r.__useRenderToTexture=!1),z.get(e.texture).__webglTexture=t,z.get(e.depthTexture).__webglTexture=r.__autoAllocateDepthBuffer?void 0:n,r.__hasExternalTextures=!0},this.setRenderTargetFramebuffer=function(e,t){let n=z.get(e);n.__webglFramebuffer=t,n.__useDefaultFramebuffer=t===void 0},this.setRenderTarget=function(e,t=0,n=0){P=e,ae=t,oe=n;let r=null,i=!1,a=!1;if(e){let o=z.get(e);if(o.__useDefaultFramebuffer!==void 0){R.bindFramebuffer(I.FRAMEBUFFER,o.__webglFramebuffer),de.copy(e.viewport),fe.copy(e.scissor),pe=e.scissorTest,R.viewport(de),R.scissor(fe),R.setScissorTest(pe),se=-1;return}if(o.__webglFramebuffer===void 0)B.setupRenderTarget(e);else if(o.__hasExternalTextures)B.rebindTextures(e,z.get(e.texture).__webglTexture,z.get(e.depthTexture).__webglTexture);else if(e.depthBuffer){let t=e.depthTexture;if(o.__boundDepthTexture!==t){if(t!==null&&z.has(t)&&(e.width!==t.image.width||e.height!==t.image.height))throw Error(`THREE.WebGLRenderer: Attached DepthTexture is initialized to the incorrect size.`);B.setupDepthRenderbuffer(e)}}let s=e.texture;(s.isData3DTexture||s.isDataArrayTexture||s.isCompressedArrayTexture)&&(a=!0);let c=z.get(e).__webglFramebuffer;e.isWebGLCubeRenderTarget?(r=Array.isArray(c[t])?c[t][n]:c[t],i=!0):r=e.samples>0&&B.useMultisampledRTT(e)===!1?z.get(e).__webglMultisampledFramebuffer:Array.isArray(c)?c[n]:c,de.copy(e.viewport),fe.copy(e.scissor),pe=e.scissorTest}else de.copy(xe).multiplyScalar(ve).floor(),fe.copy(Se).multiplyScalar(ve).floor(),pe=Ce;if(n!==0&&(r=ne),R.bindFramebuffer(I.FRAMEBUFFER,r)&&R.drawBuffers(e,r),R.viewport(de),R.scissor(fe),R.setScissorTest(pe),i){let r=z.get(e.texture);I.framebufferTexture2D(I.FRAMEBUFFER,I.COLOR_ATTACHMENT0,I.TEXTURE_CUBE_MAP_POSITIVE_X+t,r.__webglTexture,n)}else if(a){let r=t;for(let t=0;t<e.textures.length;t++){let i=z.get(e.textures[t]);I.framebufferTextureLayer(I.FRAMEBUFFER,I.COLOR_ATTACHMENT0+t,i.__webglTexture,n,r)}}else if(e!==null&&n!==0){let t=z.get(e.texture);I.framebufferTexture2D(I.FRAMEBUFFER,I.COLOR_ATTACHMENT0,I.TEXTURE_2D,t.__webglTexture,n)}se=-1};function kt(e){let t=z.get(e);return(t.__readFormat!==e.format||t.__readType!==e.type)&&(t.__readFormat=e.format,t.__readType=e.type,t.__formatReadable=Pe.textureFormatReadable(e.format),t.__typeReadable=Pe.textureTypeReadable(e.type)),t}this.readRenderTargetPixels=function(e,t,n,r,i,a,o,s=0){if(!(e&&e.isWebGLRenderTarget)){H(`WebGLRenderer.readRenderTargetPixels: renderTarget is not THREE.WebGLRenderTarget.`);return}let c=z.get(e).__webglFramebuffer;if(e.isWebGLCubeRenderTarget&&o!==void 0&&(c=c[o]),c){R.bindFramebuffer(I.FRAMEBUFFER,c);try{let o=e.textures[s],c=o.format,l=o.type;e.textures.length>1&&I.readBuffer(I.COLOR_ATTACHMENT0+s);let u=kt(o);if(u.__formatReadable===!1){H(`WebGLRenderer.readRenderTargetPixels: renderTarget is not in RGBA or implementation defined format.`);return}if(u.__typeReadable===!1){H(`WebGLRenderer.readRenderTargetPixels: renderTarget is not in UnsignedByteType or implementation defined type.`);return}t>=0&&t<=e.width-r&&n>=0&&n<=e.height-i&&I.readPixels(t,n,r,i,Ze.convert(c),Ze.convert(l),a)}finally{let e=P===null?null:z.get(P).__webglFramebuffer;R.bindFramebuffer(I.FRAMEBUFFER,e)}}},this.readRenderTargetPixelsAsync=async function(e,t,n,r,i,a,o,s=0){if(!(e&&e.isWebGLRenderTarget))throw Error(`THREE.WebGLRenderer.readRenderTargetPixels: renderTarget is not THREE.WebGLRenderTarget.`);let c=z.get(e).__webglFramebuffer;if(e.isWebGLCubeRenderTarget&&o!==void 0&&(c=c[o]),c){if(t>=0&&t<=e.width-r&&n>=0&&n<=e.height-i){R.bindFramebuffer(I.FRAMEBUFFER,c);let o=e.textures[s],l=o.format,u=o.type;e.textures.length>1&&I.readBuffer(I.COLOR_ATTACHMENT0+s);let d=kt(o);if(d.__formatReadable===!1)throw Error(`THREE.WebGLRenderer.readRenderTargetPixelsAsync: renderTarget is not in RGBA or implementation defined format.`);if(d.__typeReadable===!1)throw Error(`THREE.WebGLRenderer.readRenderTargetPixelsAsync: renderTarget is not in UnsignedByteType or implementation defined type.`);let f=I.createBuffer();I.bindBuffer(I.PIXEL_PACK_BUFFER,f),I.bufferData(I.PIXEL_PACK_BUFFER,a.byteLength,I.STREAM_READ),I.readPixels(t,n,r,i,Ze.convert(l),Ze.convert(u),0),I.bindBuffer(I.PIXEL_PACK_BUFFER,null);let p=P===null?null:z.get(P).__webglFramebuffer;R.bindFramebuffer(I.FRAMEBUFFER,p);let m=I.fenceSync(I.SYNC_GPU_COMMANDS_COMPLETE,0);return I.flush(),await dt(I,m,4),I.bindBuffer(I.PIXEL_PACK_BUFFER,f),I.getBufferSubData(I.PIXEL_PACK_BUFFER,0,a),I.bindBuffer(I.PIXEL_PACK_BUFFER,null),I.deleteBuffer(f),I.deleteSync(m),a}throw Error(`THREE.WebGLRenderer.readRenderTargetPixelsAsync: requested read bounds are out of range.`)}},this.copyFramebufferToTexture=function(e,t=null,n=0){let r=2**-n,i=Math.floor(e.image.width*r),a=Math.floor(e.image.height*r),o=t===null?0:t.x,s=t===null?0:t.y;B.setTexture2D(e,0),I.copyTexSubImage2D(I.TEXTURE_2D,n,0,0,o,s,i,a),R.unbindTexture()},this.copyTextureToTexture=function(e,t,n=null,r=null,i=0,a=0){let o,s,c,l,u,d,f,p,m,h=e.isCompressedTexture?e.mipmaps[a]:e.image;if(n!==null)o=n.max.x-n.min.x,s=n.max.y-n.min.y,c=n.isBox3?n.max.z-n.min.z:1,l=n.min.x,u=n.min.y,d=n.isBox3?n.min.z:0;else{let t=2**-i;o=Math.floor(h.width*t),s=Math.floor(h.height*t),c=e.isDataArrayTexture?h.depth:e.isData3DTexture?Math.floor(h.depth*t):1,l=0,u=0,d=0}r===null?(f=0,p=0,m=0):(f=r.x,p=r.y,m=r.z);let g=Ze.convert(t.format),_=Ze.convert(t.type),v;t.isData3DTexture?(B.setTexture3D(t,0),v=I.TEXTURE_3D):t.isDataArrayTexture||t.isCompressedArrayTexture?(B.setTexture2DArray(t,0),v=I.TEXTURE_2D_ARRAY):(B.setTexture2D(t,0),v=I.TEXTURE_2D),R.activeTexture(I.TEXTURE0),R.pixelStorei(I.UNPACK_FLIP_Y_WEBGL,t.flipY),R.pixelStorei(I.UNPACK_PREMULTIPLY_ALPHA_WEBGL,t.premultiplyAlpha),R.pixelStorei(I.UNPACK_ALIGNMENT,t.unpackAlignment);let y=R.getParameter(I.UNPACK_ROW_LENGTH),b=R.getParameter(I.UNPACK_IMAGE_HEIGHT),x=R.getParameter(I.UNPACK_SKIP_PIXELS),S=R.getParameter(I.UNPACK_SKIP_ROWS),C=R.getParameter(I.UNPACK_SKIP_IMAGES);R.pixelStorei(I.UNPACK_ROW_LENGTH,h.width),R.pixelStorei(I.UNPACK_IMAGE_HEIGHT,h.height),R.pixelStorei(I.UNPACK_SKIP_PIXELS,l),R.pixelStorei(I.UNPACK_SKIP_ROWS,u),R.pixelStorei(I.UNPACK_SKIP_IMAGES,d);let w=e.isDataArrayTexture||e.isData3DTexture,T=t.isDataArrayTexture||t.isData3DTexture;if(e.isDepthTexture){let n=z.get(e),r=z.get(t),h=z.get(n.__renderTarget),g=z.get(r.__renderTarget);R.bindFramebuffer(I.READ_FRAMEBUFFER,h.__webglFramebuffer),R.bindFramebuffer(I.DRAW_FRAMEBUFFER,g.__webglFramebuffer);for(let n=0;n<c;n++)w&&(I.framebufferTextureLayer(I.READ_FRAMEBUFFER,I.COLOR_ATTACHMENT0,z.get(e).__webglTexture,i,d+n),I.framebufferTextureLayer(I.DRAW_FRAMEBUFFER,I.COLOR_ATTACHMENT0,z.get(t).__webglTexture,a,m+n)),I.blitFramebuffer(l,u,o,s,f,p,o,s,I.DEPTH_BUFFER_BIT,I.NEAREST);R.bindFramebuffer(I.READ_FRAMEBUFFER,null),R.bindFramebuffer(I.DRAW_FRAMEBUFFER,null)}else if(i!==0||e.isRenderTargetTexture||z.has(e)){let n=z.get(e),r=z.get(t);R.bindFramebuffer(I.READ_FRAMEBUFFER,re),R.bindFramebuffer(I.DRAW_FRAMEBUFFER,ie);for(let e=0;e<c;e++)w?I.framebufferTextureLayer(I.READ_FRAMEBUFFER,I.COLOR_ATTACHMENT0,n.__webglTexture,i,d+e):I.framebufferTexture2D(I.READ_FRAMEBUFFER,I.COLOR_ATTACHMENT0,I.TEXTURE_2D,n.__webglTexture,i),T?I.framebufferTextureLayer(I.DRAW_FRAMEBUFFER,I.COLOR_ATTACHMENT0,r.__webglTexture,a,m+e):I.framebufferTexture2D(I.DRAW_FRAMEBUFFER,I.COLOR_ATTACHMENT0,I.TEXTURE_2D,r.__webglTexture,a),i===0?T?I.copyTexSubImage3D(v,a,f,p,m+e,l,u,o,s):I.copyTexSubImage2D(v,a,f,p,l,u,o,s):I.blitFramebuffer(l,u,o,s,f,p,o,s,I.COLOR_BUFFER_BIT,I.NEAREST);R.bindFramebuffer(I.READ_FRAMEBUFFER,null),R.bindFramebuffer(I.DRAW_FRAMEBUFFER,null)}else T?e.isDataTexture||e.isData3DTexture?I.texSubImage3D(v,a,f,p,m,o,s,c,g,_,h.data):t.isCompressedArrayTexture?I.compressedTexSubImage3D(v,a,f,p,m,o,s,c,g,h.data):I.texSubImage3D(v,a,f,p,m,o,s,c,g,_,h):e.isDataTexture?I.texSubImage2D(I.TEXTURE_2D,a,f,p,o,s,g,_,h.data):e.isCompressedTexture?I.compressedTexSubImage2D(I.TEXTURE_2D,a,f,p,h.width,h.height,g,h.data):I.texSubImage2D(I.TEXTURE_2D,a,f,p,o,s,g,_,h);R.pixelStorei(I.UNPACK_ROW_LENGTH,y),R.pixelStorei(I.UNPACK_IMAGE_HEIGHT,b),R.pixelStorei(I.UNPACK_SKIP_PIXELS,x),R.pixelStorei(I.UNPACK_SKIP_ROWS,S),R.pixelStorei(I.UNPACK_SKIP_IMAGES,C),a===0&&t.generateMipmaps&&I.generateMipmap(v),R.unbindTexture()},this.initRenderTarget=function(e){z.get(e).__webglFramebuffer===void 0&&B.setupRenderTarget(e)},this.initTexture=function(e){e.isCubeTexture?B.setTextureCube(e,0):e.isData3DTexture?B.setTexture3D(e,0):e.isDataArrayTexture||e.isCompressedArrayTexture?B.setTexture2DArray(e,0):B.setTexture2D(e,0),R.unbindTexture()},this.resetState=function(){ae=0,oe=0,P=null,R.reset(),Qe.reset()},typeof __THREE_DEVTOOLS__<`u`&&__THREE_DEVTOOLS__.dispatchEvent(new CustomEvent(`observe`,{detail:this}))}get coordinateSystem(){return nt}get outputColorSpace(){return this._outputColorSpace}set outputColorSpace(e){this._outputColorSpace=e;let t=this.getContext();t.drawingBufferColorSpace=q._getDrawingBufferColorSpace(e),t.unpackColorSpace=q._getUnpackColorSpace()}},Al={type:`change`},jl={type:`start`},Ml={type:`end`},Nl=new Yr,Pl=new Hr,Fl=Math.cos(70*zt.DEG2RAD),Il=new G,Ll=2*Math.PI,$={NONE:-1,ROTATE:0,DOLLY:1,PAN:2,TOUCH_ROTATE:3,TOUCH_PAN:4,TOUCH_DOLLY_PAN:5,TOUCH_DOLLY_ROTATE:6},Rl=1e-6,zl=class extends lo{constructor(e,t=null){super(e,t),this.state=$.NONE,this.target=new G,this.cursor=new G,this.minDistance=0,this.maxDistance=1/0,this.minZoom=0,this.maxZoom=1/0,this.minTargetRadius=0,this.maxTargetRadius=1/0,this.minPolarAngle=0,this.maxPolarAngle=Math.PI,this.minAzimuthAngle=-1/0,this.maxAzimuthAngle=1/0,this.enableDamping=!1,this.dampingFactor=.05,this.enableZoom=!0,this.zoomSpeed=1,this.enableRotate=!0,this.rotateSpeed=1,this.keyRotateSpeed=1,this.enablePan=!0,this.panSpeed=1,this.screenSpacePanning=!0,this.keyPanSpeed=7,this.zoomToCursor=!1,this.autoRotate=!1,this.autoRotateSpeed=2,this.keys={LEFT:`ArrowLeft`,UP:`ArrowUp`,RIGHT:`ArrowRight`,BOTTOM:`ArrowDown`},this.mouseButtons={LEFT:p.ROTATE,MIDDLE:p.DOLLY,RIGHT:p.PAN},this.touches={ONE:m.ROTATE,TWO:m.DOLLY_PAN},this.target0=this.target.clone(),this.position0=this.object.position.clone(),this.zoom0=this.object.zoom,this._cursorStyle=`auto`,this._domElementKeyEvents=null,this._lastPosition=new G,this._lastQuaternion=new Bt,this._lastTargetPosition=new G,this._quat=new Bt().setFromUnitVectors(e.up,new G(0,1,0)),this._quatInverse=this._quat.clone().invert(),this._spherical=new so,this._sphericalDelta=new so,this._scale=1,this._panOffset=new G,this._rotateStart=new W,this._rotateEnd=new W,this._rotateDelta=new W,this._panStart=new W,this._panEnd=new W,this._panDelta=new W,this._dollyStart=new W,this._dollyEnd=new W,this._dollyDelta=new W,this._dollyDirection=new G,this._mouse=new W,this._performCursorZoom=!1,this._pointers=[],this._pointerPositions={},this._controlActive=!1,this._onPointerMove=Vl.bind(this),this._onPointerDown=Bl.bind(this),this._onPointerUp=Hl.bind(this),this._onContextMenu=Yl.bind(this),this._onMouseWheel=Gl.bind(this),this._onKeyDown=Kl.bind(this),this._onTouchStart=ql.bind(this),this._onTouchMove=Jl.bind(this),this._onMouseDown=Ul.bind(this),this._onMouseMove=Wl.bind(this),this._interceptControlDown=Xl.bind(this),this._interceptControlUp=Zl.bind(this),this.domElement!==null&&this.connect(this.domElement),this.update()}set cursorStyle(e){this._cursorStyle=e,e===`grab`?this.domElement.style.cursor=`grab`:this.domElement.style.cursor=`auto`}get cursorStyle(){return this._cursorStyle}connect(e){super.connect(e),this.domElement.addEventListener(`pointerdown`,this._onPointerDown),this.domElement.addEventListener(`pointercancel`,this._onPointerUp),this.domElement.addEventListener(`contextmenu`,this._onContextMenu),this.domElement.addEventListener(`wheel`,this._onMouseWheel,{passive:!1}),this.domElement.getRootNode().addEventListener(`keydown`,this._interceptControlDown,{passive:!0,capture:!0}),this.domElement.style.touchAction=`none`}disconnect(){this.state=$.NONE,this.domElement.removeEventListener(`pointerdown`,this._onPointerDown),this.domElement.ownerDocument.removeEventListener(`pointermove`,this._onPointerMove),this.domElement.ownerDocument.removeEventListener(`pointerup`,this._onPointerUp),this.domElement.removeEventListener(`pointercancel`,this._onPointerUp),this.domElement.removeEventListener(`wheel`,this._onMouseWheel),this.domElement.removeEventListener(`contextmenu`,this._onContextMenu),this.stopListenToKeyEvents();let e=this.domElement.getRootNode();e.removeEventListener(`keydown`,this._interceptControlDown,{capture:!0}),e.removeEventListener(`keyup`,this._interceptControlUp,{capture:!0}),this._controlActive=!1,this._pointers.length=0,this._pointerPositions={},this.domElement.style.touchAction=``,this.domElement.style.cursor=`auto`}dispose(){this.disconnect()}getPolarAngle(){return this._spherical.phi}getAzimuthalAngle(){return this._spherical.theta}getDistance(){return this.object.position.distanceTo(this.target)}listenToKeyEvents(e){e.addEventListener(`keydown`,this._onKeyDown),this._domElementKeyEvents=e}stopListenToKeyEvents(){this._domElementKeyEvents!==null&&(this._domElementKeyEvents.removeEventListener(`keydown`,this._onKeyDown),this._domElementKeyEvents=null)}saveState(){this.target0.copy(this.target),this.position0.copy(this.object.position),this.zoom0=this.object.zoom}reset(){this.target.copy(this.target0),this.object.position.copy(this.position0),this.object.zoom=this.zoom0,this.object.updateProjectionMatrix(),this.dispatchEvent(Al),this.update(),this.state=$.NONE}pan(e,t){this._pan(e,t),this.update()}dollyIn(e){this._dollyIn(e),this.update()}dollyOut(e){this._dollyOut(e),this.update()}rotateLeft(e){this._rotateLeft(e),this.update()}rotateUp(e){this._rotateUp(e),this.update()}update(e=null){let t=this.object.position;Il.copy(t).sub(this.target),Il.applyQuaternion(this._quat),this._spherical.setFromVector3(Il),this.autoRotate&&this.state===$.NONE&&this._rotateLeft(this._getAutoRotationAngle(e)),this.enableDamping?(this._spherical.theta+=this._sphericalDelta.theta*this.dampingFactor,this._spherical.phi+=this._sphericalDelta.phi*this.dampingFactor):(this._spherical.theta+=this._sphericalDelta.theta,this._spherical.phi+=this._sphericalDelta.phi);let n=this.minAzimuthAngle,r=this.maxAzimuthAngle;isFinite(n)&&isFinite(r)&&(n<-Math.PI?n+=Ll:n>Math.PI&&(n-=Ll),r<-Math.PI?r+=Ll:r>Math.PI&&(r-=Ll),n<=r?this._spherical.theta=Math.max(n,Math.min(r,this._spherical.theta)):this._spherical.theta=this._spherical.theta>(n+r)/2?Math.max(n,this._spherical.theta):Math.min(r,this._spherical.theta)),this._spherical.phi=Math.max(this.minPolarAngle,Math.min(this.maxPolarAngle,this._spherical.phi)),this._spherical.makeSafe(),this.enableDamping===!0?this.target.addScaledVector(this._panOffset,this.dampingFactor):this.target.add(this._panOffset),this.target.sub(this.cursor),this.target.clampLength(this.minTargetRadius,this.maxTargetRadius),this.target.add(this.cursor);let i=!1;if(this.zoomToCursor&&this._performCursorZoom||this.object.isOrthographicCamera)this._spherical.radius=this._clampDistance(this._spherical.radius);else{let e=this._spherical.radius;this._spherical.radius=this._clampDistance(this._spherical.radius*this._scale),i=e!=this._spherical.radius}if(Il.setFromSpherical(this._spherical),Il.applyQuaternion(this._quatInverse),t.copy(this.target).add(Il),this.object.lookAt(this.target),this.enableDamping===!0?(this._sphericalDelta.theta*=1-this.dampingFactor,this._sphericalDelta.phi*=1-this.dampingFactor,this._panOffset.multiplyScalar(1-this.dampingFactor)):(this._sphericalDelta.set(0,0,0),this._panOffset.set(0,0,0)),this.zoomToCursor&&this._performCursorZoom){let e=null;if(this.object.isPerspectiveCamera){let t=Il.length();e=this._clampDistance(t*this._scale);let n=t-e;this.object.position.addScaledVector(this._dollyDirection,n),this.object.updateMatrixWorld(),i=!!n}else if(this.object.isOrthographicCamera){let t=new G(this._mouse.x,this._mouse.y,0);t.unproject(this.object);let n=this.object.zoom;this.object.zoom=Math.max(this.minZoom,Math.min(this.maxZoom,this.object.zoom/this._scale)),this.object.updateProjectionMatrix(),i=n!==this.object.zoom;let r=new G(this._mouse.x,this._mouse.y,0);r.unproject(this.object),this.object.position.sub(r).add(t),this.object.updateMatrixWorld(),e=Il.length()}else console.warn(`WARNING: OrbitControls.js encountered an unknown camera type - zoom to cursor disabled.`),this.zoomToCursor=!1;e!==null&&(this.screenSpacePanning?this.target.set(0,0,-1).transformDirection(this.object.matrix).multiplyScalar(e).add(this.object.position):(Nl.origin.copy(this.object.position),Nl.direction.set(0,0,-1).transformDirection(this.object.matrix),Math.abs(this.object.up.dot(Nl.direction))<Fl?this.object.lookAt(this.target):(Pl.setFromNormalAndCoplanarPoint(this.object.up,this.target),Nl.intersectPlane(Pl,this.target))))}else if(this.object.isOrthographicCamera){let e=this.object.zoom;this.object.zoom=Math.max(this.minZoom,Math.min(this.maxZoom,this.object.zoom/this._scale)),e!==this.object.zoom&&(this.object.updateProjectionMatrix(),i=!0)}return this._scale=1,this._performCursorZoom=!1,i||this._lastPosition.distanceToSquared(this.object.position)>Rl||8*(1-this._lastQuaternion.dot(this.object.quaternion))>Rl||this._lastTargetPosition.distanceToSquared(this.target)>Rl?(this.dispatchEvent(Al),this._lastPosition.copy(this.object.position),this._lastQuaternion.copy(this.object.quaternion),this._lastTargetPosition.copy(this.target),!0):!1}_getAutoRotationAngle(e){return e===null?Ll/60/60*this.autoRotateSpeed:Ll/60*this.autoRotateSpeed*e}_getZoomScale(e){let t=Math.abs(e*.01);return .95**(this.zoomSpeed*t)}_rotateLeft(e){this._sphericalDelta.theta-=e}_rotateUp(e){this._sphericalDelta.phi-=e}_panLeft(e,t){Il.setFromMatrixColumn(t,0),Il.multiplyScalar(-e),this._panOffset.add(Il)}_panUp(e,t){this.screenSpacePanning===!0?Il.setFromMatrixColumn(t,1):(Il.setFromMatrixColumn(t,0),Il.crossVectors(this.object.up,Il)),Il.multiplyScalar(e),this._panOffset.add(Il)}_pan(e,t){let n=this.domElement;if(this.object.isPerspectiveCamera){let r=this.object.position;Il.copy(r).sub(this.target);let i=Il.length();i*=Math.tan(this.object.fov/2*Math.PI/180),this._panLeft(2*e*i/n.clientHeight,this.object.matrix),this._panUp(2*t*i/n.clientHeight,this.object.matrix)}else this.object.isOrthographicCamera?(this._panLeft(e*(this.object.right-this.object.left)/this.object.zoom/n.clientWidth,this.object.matrix),this._panUp(t*(this.object.top-this.object.bottom)/this.object.zoom/n.clientHeight,this.object.matrix)):(console.warn(`WARNING: OrbitControls.js encountered an unknown camera type - pan disabled.`),this.enablePan=!1)}_dollyOut(e){this.object.isPerspectiveCamera||this.object.isOrthographicCamera?this._scale/=e:(console.warn(`WARNING: OrbitControls.js encountered an unknown camera type - dolly/zoom disabled.`),this.enableZoom=!1)}_dollyIn(e){this.object.isPerspectiveCamera||this.object.isOrthographicCamera?this._scale*=e:(console.warn(`WARNING: OrbitControls.js encountered an unknown camera type - dolly/zoom disabled.`),this.enableZoom=!1)}_updateZoomParameters(e,t){if(!this.zoomToCursor)return;this._performCursorZoom=!0;let n=this.domElement.getBoundingClientRect(),r=e-n.left,i=t-n.top,a=n.width,o=n.height;this._mouse.x=r/a*2-1,this._mouse.y=-(i/o)*2+1,this._dollyDirection.set(this._mouse.x,this._mouse.y,1).unproject(this.object).sub(this.object.position).normalize()}_clampDistance(e){return Math.max(this.minDistance,Math.min(this.maxDistance,e))}_handleMouseDownRotate(e){this._rotateStart.set(e.clientX,e.clientY)}_handleMouseDownDolly(e){this._updateZoomParameters(e.clientX,e.clientX),this._dollyStart.set(e.clientX,e.clientY)}_handleMouseDownPan(e){this._panStart.set(e.clientX,e.clientY)}_handleMouseMoveRotate(e){this._rotateEnd.set(e.clientX,e.clientY),this._rotateDelta.subVectors(this._rotateEnd,this._rotateStart).multiplyScalar(this.rotateSpeed);let t=this.domElement;this._rotateLeft(Ll*this._rotateDelta.x/t.clientHeight),this._rotateUp(Ll*this._rotateDelta.y/t.clientHeight),this._rotateStart.copy(this._rotateEnd),this.update()}_handleMouseMoveDolly(e){this._dollyEnd.set(e.clientX,e.clientY),this._dollyDelta.subVectors(this._dollyEnd,this._dollyStart),this._dollyDelta.y>0?this._dollyOut(this._getZoomScale(this._dollyDelta.y)):this._dollyDelta.y<0&&this._dollyIn(this._getZoomScale(this._dollyDelta.y)),this._dollyStart.copy(this._dollyEnd),this.update()}_handleMouseMovePan(e){this._panEnd.set(e.clientX,e.clientY),this._panDelta.subVectors(this._panEnd,this._panStart).multiplyScalar(this.panSpeed),this._pan(this._panDelta.x,this._panDelta.y),this._panStart.copy(this._panEnd),this.update()}_handleMouseWheel(e){this._updateZoomParameters(e.clientX,e.clientY),e.deltaY<0?this._dollyIn(this._getZoomScale(e.deltaY)):e.deltaY>0&&this._dollyOut(this._getZoomScale(e.deltaY)),this.update()}_handleKeyDown(e){let t=!1;switch(e.code){case this.keys.UP:e.ctrlKey||e.metaKey||e.shiftKey?this.enableRotate&&this._rotateUp(Ll*this.keyRotateSpeed/this.domElement.clientHeight):this.enablePan&&this._pan(0,this.keyPanSpeed),t=!0;break;case this.keys.BOTTOM:e.ctrlKey||e.metaKey||e.shiftKey?this.enableRotate&&this._rotateUp(-Ll*this.keyRotateSpeed/this.domElement.clientHeight):this.enablePan&&this._pan(0,-this.keyPanSpeed),t=!0;break;case this.keys.LEFT:e.ctrlKey||e.metaKey||e.shiftKey?this.enableRotate&&this._rotateLeft(Ll*this.keyRotateSpeed/this.domElement.clientHeight):this.enablePan&&this._pan(this.keyPanSpeed,0),t=!0;break;case this.keys.RIGHT:e.ctrlKey||e.metaKey||e.shiftKey?this.enableRotate&&this._rotateLeft(-Ll*this.keyRotateSpeed/this.domElement.clientHeight):this.enablePan&&this._pan(-this.keyPanSpeed,0),t=!0}t&&(e.preventDefault(),this.update())}_handleTouchStartRotate(e){if(this._pointers.length===1)this._rotateStart.set(e.pageX,e.pageY);else{let t=this._getSecondPointerPosition(e),n=.5*(e.pageX+t.x),r=.5*(e.pageY+t.y);this._rotateStart.set(n,r)}}_handleTouchStartPan(e){if(this._pointers.length===1)this._panStart.set(e.pageX,e.pageY);else{let t=this._getSecondPointerPosition(e),n=.5*(e.pageX+t.x),r=.5*(e.pageY+t.y);this._panStart.set(n,r)}}_handleTouchStartDolly(e){let t=this._getSecondPointerPosition(e),n=e.pageX-t.x,r=e.pageY-t.y,i=Math.sqrt(n*n+r*r);this._dollyStart.set(0,i)}_handleTouchStartDollyPan(e){this.enableZoom&&this._handleTouchStartDolly(e),this.enablePan&&this._handleTouchStartPan(e)}_handleTouchStartDollyRotate(e){this.enableZoom&&this._handleTouchStartDolly(e),this.enableRotate&&this._handleTouchStartRotate(e)}_handleTouchMoveRotate(e){if(this._pointers.length==1)this._rotateEnd.set(e.pageX,e.pageY);else{let t=this._getSecondPointerPosition(e),n=.5*(e.pageX+t.x),r=.5*(e.pageY+t.y);this._rotateEnd.set(n,r)}this._rotateDelta.subVectors(this._rotateEnd,this._rotateStart).multiplyScalar(this.rotateSpeed);let t=this.domElement;this._rotateLeft(Ll*this._rotateDelta.x/t.clientHeight),this._rotateUp(Ll*this._rotateDelta.y/t.clientHeight),this._rotateStart.copy(this._rotateEnd)}_handleTouchMovePan(e){if(this._pointers.length===1)this._panEnd.set(e.pageX,e.pageY);else{let t=this._getSecondPointerPosition(e),n=.5*(e.pageX+t.x),r=.5*(e.pageY+t.y);this._panEnd.set(n,r)}this._panDelta.subVectors(this._panEnd,this._panStart).multiplyScalar(this.panSpeed),this._pan(this._panDelta.x,this._panDelta.y),this._panStart.copy(this._panEnd)}_handleTouchMoveDolly(e){let t=this._getSecondPointerPosition(e),n=e.pageX-t.x,r=e.pageY-t.y,i=Math.sqrt(n*n+r*r);this._dollyEnd.set(0,i),this._dollyDelta.set(0,(this._dollyEnd.y/this._dollyStart.y)**+this.zoomSpeed),this._dollyOut(this._dollyDelta.y),this._dollyStart.copy(this._dollyEnd);let a=(e.pageX+t.x)*.5,o=(e.pageY+t.y)*.5;this._updateZoomParameters(a,o)}_handleTouchMoveDollyPan(e){this.enableZoom&&this._handleTouchMoveDolly(e),this.enablePan&&this._handleTouchMovePan(e)}_handleTouchMoveDollyRotate(e){this.enableZoom&&this._handleTouchMoveDolly(e),this.enableRotate&&this._handleTouchMoveRotate(e)}_addPointer(e){this._pointers.push(e.pointerId)}_removePointer(e){delete this._pointerPositions[e.pointerId];for(let t=0;t<this._pointers.length;t++)if(this._pointers[t]==e.pointerId){this._pointers.splice(t,1);return}}_isTrackingPointer(e){for(let t=0;t<this._pointers.length;t++)if(this._pointers[t]==e.pointerId)return!0;return!1}_trackPointer(e){let t=this._pointerPositions[e.pointerId];t===void 0&&(t=new W,this._pointerPositions[e.pointerId]=t),t.set(e.pageX,e.pageY)}_getSecondPointerPosition(e){let t=e.pointerId===this._pointers[0]?this._pointers[1]:this._pointers[0];return this._pointerPositions[t]}_customWheelEvent(e){let t=e.deltaMode,n={clientX:e.clientX,clientY:e.clientY,deltaY:e.deltaY};switch(t){case 1:n.deltaY*=16;break;case 2:n.deltaY*=100}return e.ctrlKey&&!this._controlActive&&(n.deltaY*=10),n}};function Bl(e){this.enabled!==!1&&(this._pointers.length===0&&(this.domElement.setPointerCapture(e.pointerId),this.domElement.ownerDocument.addEventListener(`pointermove`,this._onPointerMove),this.domElement.ownerDocument.addEventListener(`pointerup`,this._onPointerUp)),!this._isTrackingPointer(e)&&(this._addPointer(e),e.pointerType===`touch`?this._onTouchStart(e):this._onMouseDown(e),this._cursorStyle===`grab`&&(this.domElement.style.cursor=`grabbing`)))}function Vl(e){this.enabled!==!1&&(e.pointerType===`touch`?this._onTouchMove(e):this._onMouseMove(e))}function Hl(e){switch(this._removePointer(e),this._pointers.length){case 0:this.domElement.releasePointerCapture(e.pointerId),this.domElement.ownerDocument.removeEventListener(`pointermove`,this._onPointerMove),this.domElement.ownerDocument.removeEventListener(`pointerup`,this._onPointerUp),this.dispatchEvent(Ml),this.state=$.NONE,this._cursorStyle===`grab`&&(this.domElement.style.cursor=`grab`);break;case 1:let t=this._pointers[0],n=this._pointerPositions[t];this._onTouchStart({pointerId:t,pageX:n.x,pageY:n.y})}}function Ul(e){let t;switch(e.button){case 0:t=this.mouseButtons.LEFT;break;case 1:t=this.mouseButtons.MIDDLE;break;case 2:t=this.mouseButtons.RIGHT;break;default:t=-1}switch(t){case p.DOLLY:if(this.enableZoom===!1)return;this._handleMouseDownDolly(e),this.state=$.DOLLY;break;case p.ROTATE:if(e.ctrlKey||e.metaKey||e.shiftKey){if(this.enablePan===!1)return;this._handleMouseDownPan(e),this.state=$.PAN}else{if(this.enableRotate===!1)return;this._handleMouseDownRotate(e),this.state=$.ROTATE}break;case p.PAN:if(e.ctrlKey||e.metaKey||e.shiftKey){if(this.enableRotate===!1)return;this._handleMouseDownRotate(e),this.state=$.ROTATE}else{if(this.enablePan===!1)return;this._handleMouseDownPan(e),this.state=$.PAN}break;default:this.state=$.NONE}this.state!==$.NONE&&this.dispatchEvent(jl)}function Wl(e){switch(this.state){case $.ROTATE:if(this.enableRotate===!1)return;this._handleMouseMoveRotate(e);break;case $.DOLLY:if(this.enableZoom===!1)return;this._handleMouseMoveDolly(e);break;case $.PAN:if(this.enablePan===!1)return;this._handleMouseMovePan(e)}}function Gl(e){this.enabled!==!1&&this.enableZoom!==!1&&this.state===$.NONE&&(e.preventDefault(),this.dispatchEvent(jl),this._handleMouseWheel(this._customWheelEvent(e)),this.dispatchEvent(Ml))}function Kl(e){this.enabled!==!1&&this._handleKeyDown(e)}function ql(e){switch(this._trackPointer(e),this._pointers.length){case 1:switch(this.touches.ONE){case m.ROTATE:if(this.enableRotate===!1)return;this._handleTouchStartRotate(e),this.state=$.TOUCH_ROTATE;break;case m.PAN:if(this.enablePan===!1)return;this._handleTouchStartPan(e),this.state=$.TOUCH_PAN;break;default:this.state=$.NONE}break;case 2:switch(this.touches.TWO){case m.DOLLY_PAN:if(this.enableZoom===!1&&this.enablePan===!1)return;this._handleTouchStartDollyPan(e),this.state=$.TOUCH_DOLLY_PAN;break;case m.DOLLY_ROTATE:if(this.enableZoom===!1&&this.enableRotate===!1)return;this._handleTouchStartDollyRotate(e),this.state=$.TOUCH_DOLLY_ROTATE;break;default:this.state=$.NONE}break;default:this.state=$.NONE}this.state!==$.NONE&&this.dispatchEvent(jl)}function Jl(e){switch(this._trackPointer(e),this.state){case $.TOUCH_ROTATE:if(this.enableRotate===!1)return;this._handleTouchMoveRotate(e),this.update();break;case $.TOUCH_PAN:if(this.enablePan===!1)return;this._handleTouchMovePan(e),this.update();break;case $.TOUCH_DOLLY_PAN:if(this.enableZoom===!1&&this.enablePan===!1)return;this._handleTouchMoveDollyPan(e),this.update();break;case $.TOUCH_DOLLY_ROTATE:if(this.enableZoom===!1&&this.enableRotate===!1)return;this._handleTouchMoveDollyRotate(e),this.update();break;default:this.state=$.NONE}}function Yl(e){this.enabled!==!1&&e.preventDefault()}function Xl(e){e.key===`Control`&&(this._controlActive=!0,this.domElement.getRootNode().addEventListener(`keyup`,this._interceptControlUp,{passive:!0,capture:!0}))}function Zl(e){e.key===`Control`&&(this._controlActive=!1,this.domElement.getRootNode().removeEventListener(`keyup`,this._interceptControlUp,{passive:!0,capture:!0}))}var Ql={textures:JSON.parse(`{"cccloud:block/mainframe":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAIAAAACACAYAAADDPmHLAAAPUklEQVR42u2bzap0xRWGzy04F3EgiIgogiCI6Fgj5AIk4iUkh54I4sCZEwlxFJI7EESIg1xJcjTeQPy+azjpt85+qp9avbsVJHyarMFi/9RP117rXb9VffPGG2/cf393e//td7f3//rnH473h0F5/v4fvx9t47pR+nz37UOfV1999f6ll14afTLPw/jbOYY5c2VM3r/++uvj+vLLL4/7tL/wwgvjXfrRf+8+c7/22mtj3ox95ZVXxv3DOg5zzWMtmi/vWEPG5z5jsu5c8/uZK9+U9rzLc66sM5T7vM+49KMt9/AjlOf0SVue08aYXEP+zVzzTB/uc2Usz2n3uFz9nrG08z1ZS9pyZa6bwfiNYWbaZNwmyAdgnJiYdycAPDD1QfiH2X/03eazUDIuzzAyfbMowAIIGec15JpxjM9cGTd+fwj/MIHINy3g3oCXuTP2YS0n4NRvZBy8QAH4TdbN79Lmb+FdVTQEBd9o5/cg5jVvco/AHz36y/3jf/9xyu2kyCdlPD0f7h89/tP9ox++GHy4GQzYPqxqLx8NQ/yh6Qfy0m9o9d1JyxfhT+YepubmOQsIAyzUBXC2QPqAgC59Mj5M4H4FwCq0+T0bWHNvrcqa8j1oCZrHN2Z9WBzaMxaLkCvfw3yeg+f0weLQhiKlrfbhd/k9rEquKMAPj/48QFCBVy0g7fRfLADahsBOZlsuYGsHkRYgmmxN8LMXBAAwwRMA0jDMKkyYH3bsgwvAfOcdfU5gOSxWyL+fcawfd1StlBmHZpqx6YfQWYs1z8pSNRlrhTvAAp5ZP1klKxcAhjer5b1dLMmcR4oAPwKeG5ixmph1Au6nm7g7xQAwIIthnmpRqmAyBq0FAEODN7A9gPIgK3A4CXizADCS8byrAqsgBgDMyfglvlFfK4aFiAtAAFjCE49ul/F2S7ZAxFAP8xwWRVvBfJobZbEC0ddxF/yyS/OaMv4G5NEwfYjM7yJ4hLsFcwYA7V6QrQu/g98muCEgw68tANrWYTfxAJbDNM+5P1mgVQP8zLfht7EwZyDV1bw4Me8wAch61xhm5efq2m7ntxKo8W2Tb9vvW/lsebE2BHjVMjtmqHwwODP+BjTt+cqpQUIiDEHoLIYPsebX8bQBOjQYc2xzVjMO3uEr+U2CUATpb7ErsCsaYNmCUdyRf2cx/7YE1s5tnhoDnWv/4SymwgI4hoIPzrhOJv2wxEDm2bScd2vQuPBAsZhllHluHhB0WBjgCNRRffWNBGF8lK2FNcpRMGkcQkNw3NvMLn5tY8r0exsY8OFola2GNcMBLlYDADpCN8htueZVgiGAmwAoluIse9ra+ea5/gJgu1ELlN/N92MJsDz274siL0I/nMVZN/ieVXsPy8JrQMMPogFOrarZs1k08hAaJszaVHN5p4Tk3sQN/D6MxOrY5C/mfPP9MN1Z0Lr+w1nc4zigZjFYIpvhKkTmQQFwgcRF5pWDPyskLhQLQAxR4zN/j8HrbxlZwLvvvjtSgkHH3DApQnLK5Iq5Pn781/F+3G/vBx37ZyEZn/cRQPrSn/nGPHnHuOO7LDr3Gf/OO++Md6Q2o2/G0n8jUhdSn/QLA956660xPh/DGuc6tjV4ztwzntSvaryLV46P/B4GkpFMDV7M9TmIGQfosWbENYvlUzZWC3AAYPBvk0d4NPgkGcE3eDhluMnhBgaOBgluCnKbaAoh77b2KcBjeyYz06cQ6/ORwngW8N5774158zF5NwobWz/WMBZ+vA/D8pzfBUCsHwDywV7//PBtrsyTq004wqoVyFoI8z0W4CTA2wsB5GGJ8B34YQGXIPBurZyehH87LRdBYPjn756yFAis4JMvRxoxABZgarsGTU2CifzQ1icLiQDyPouhbWqh5xTlo9HajM+4CBBBDUsgpOYeq5H5Mi6/n36DAcf2zGm02+Iw75jz2E7fKQAVu6ypNp3nGdHtLP+SWdRsx2ByUQ1ttwUACNXKOLV1BZa4A/7BL+S0KC+gkJXPNb85LQCDbUJg2tQq/QgaxAKGgLAOGrNct/YBgOM8GTMAeLwPI+w+XHsHMCwesOV9AHDRAhWLxloAmAGw53uX7MXVyC0gpW5/CuLWvktd4W4twNRSOlpdUzWn1bzPGPY/4J9d3HS9gB7Nd79NqW5gNBsWpFnUub3ZUTdIfM9YiiO1bFnLm6QwROK8JzeuGyK8Zw30ZW2sAa3Cv3t+v3O5txZSXLo+K4gpkGL9MwYoJWv7cKeS+PBayVzqKLIGrqXULCAueDH/JQ5DKWpsgNLdYMLtaxdNkkZPFG3vM8GwINsPzOCx+N9qDbAmxBDz93ZQXAPT6ZqOz7iQafaFfH8s5s8aQRDrXciV6YczITq9ciXSxR1rfU0FuSJ0V1LZIKsbPrUy6FIyMRSu9qL5lzuefbbvv2HL0Fua3rhAa8hZqX7RF02mD5qFBcFyeJPDV6wDKR390G5rv/syB/P7OxAsc+5ZEf++tawWX2pAWGvyrkPU1KtW5ACIdyMBIOmcf28p3coVYLVsVbHWtsjwAIsLv/juYQEwiexyMdh73rgJCwIz6tqBTalzUm+ROi9Pf6zL1HJp+AwCN8Q6xWPcm2++Oejtt9+exLtKgCp9+J6nn376/tlnn71/5plnxn2ueeYdz+7nd5988sn9p59+ulw/+uijcf/xxx+Pa57znmdfv/rqq0Fffvnl/ddffz2u33zzzaC8zzs/55o+jHn//ffHPCF+w2vJc36fNbGW3H/wwQcPlUAYAplZ9uXpl3v6Y7YcHbuKtWrEuqmERkzftLmVWXMQCCZAlIXQh7WxZtbn57p+E8K0YLk3IHJvoh/MRQgwvgrD5LYIEUK4CNzCRvjcBxi5RogGnNdg4HktvP/www8fKoEwywKuzKuMyxhM2bqJsVbgHDhZ8JjDsxTG9QjHFiWHJTaoYK1A2Lv6exC2BV0BsAcM2qrQq+BrOyCgjwUdoSJYBI4FoK2CIELcE261CLYG3Md63KAZNqWXmFmBQTTr7VNvZCwl0K193ZO+XQpG07wX7Xdl0kElKR1CtWDrWg1yvjdXC9euADfge4OF/haoLUFluIXgvhY8lsBu4RIIGBchXpr7kjsAlMMFwDgzzAyqGmQmE/jUKtpy9MnpVdncyf3U8i3DWPy/ijquI7hOwTqrj0fo/g7aTBbmnqBtBSql7ZLZ5d5gcB/eV41HsDybbPoBRIRY3QqWwBbI7of74QKqplcm2hrUfhyurAc31x2927PdKOe0MzWheOSiDQJ3LRuAbBbD6/KaiQscIPp7+Maf4gLs92ufqm3WRL+rQuIeoVq7KxBsDRwn5H2EaAtTg88aBBoEwwWQ3iFka48BUYWPNag7WMtRpLsVALXClfZp0iVob0J5I8ibPfTbCwCxCDV2sbVg3DWzX63DnqWwRsHoPRBUd0DfavZtCaq/x004VogQ99wMAvfveY2h4QJ8TLhqh92BtYx2cte9mnk9RLF3IiVts3Cjwo81fKlgqdKFm7ALuxSzVEDQL9dqAS6Z+goSA8BpnU3wnmvw+5BTPYCAcP28FyPgApyJGJDX/H+u0wVYyyuTEPieBfBBUJ8fWDcwSnygYDD3davWkb7TPyzEdBMbSKpG11iFthrjcH9J66/5fQMGpluzDIC9wMzCsaBrjm//70CQ9hAuwAD0mqrfd9/hAqieXfLze0Eg/TioWY8y+YDJWkFbj4yl7ewMgazAsounNHC+3+oAFnQFgN2Av4+rfbrz/b14YK9QZA2r2lbTwioo6gA26TXVMyF0xwwUguo6KuBcJ1iyAJddL8UBl+oEsxC0c2J4SQ3LnzV8wmfx9zprYM13UOiUMICx9jvKr1rvQlctBF2L9Gt6WNsuRdo1596LD3JfhbwX8WP+3c+FoKrheyDYSwuHC4gfr6lSdQcVDNauvT1sn2GrBxTrsXGCOwsc04//XzKCskm0B9DqymocY2tg/16DvqrxewCoRR9rd/X7ewKp1b5qEWpcYDDgAgyuvWjfFsD30wU4h4Z53FctqgUh75RVK7BUCe/ODzbkvXenXBauO4vLfoGCweqmagp7Ddi5WrA2/bU4VN0Afczsvf0AA8EFI6gK29G/3YH9v9NDsoBa53dcshd8ziyAPxfs+cy9EnDd4z/bry5Bnk/ULid0NytQD5x4w8fbyLOfLAIAqIFqXfdeYcsWoGr2nqZfqgba19dArGrlXqWwln1/LA6oAWKE6LSvBn57a1v2AgBA9f9V2HvMZVuyngR2XGBhe6eQgpFPGvn4EkKuxR/XDLwXUNd7KSCsMULN76/FAdUN5Lkyt1qAPcbbBdS0zuVfv7dVcNbgvYCa5tVNqBosDhfg0zHeW+aZff9aL2Af3mfq946We4+9/m1pZAESrE8e7x1Mmdqv9r20td67VlDb667fNcHvpYx1y3evIONUsFbr9sq/jgX2toadMroQVH+rBqS2EDMLeOqpp+5/DoWRtRhSo04OJeyRzyNwgIMDG5w94F+5e7S3gVX3AmwhLs3z/0o/GwARXoT83HPPDXr++ecHELx3fg0AadvbjfOzzwE2/cIAEI2NpjuazbODKzR8jyjG7JVjMbnXAMDp3qYnBIBocC1O1JLlj2nwJcHja6+Z7gbAEwYAMUCNbh0AXQNA/PzelqtBcM2CNAB+AQCI74+gc43QX3zxxZ/sw9OWfl98/tv7zz/7zdk1bddiiAbAEwZAJtk7f+Z8mT99XAoCOZPgZ588vvYBDYBfQBawV7M2/TfTL/4x3PSELcCe/6focM2Eu06f51iPFDdojzupAufvUfyTuAX5hNNAagAReOoAuXdEfy0GCAAyJsWbPFPavNQ/wv/73353cgHbP5ObnqALcP6fFDBabwBcq+S5jNsC+RUCIGlcPc5EHYB69rU0rul/IAZoagA0NQCaGgBNvz4ANBMaAM2IBkBTA6CpAdDUAGhqADQ1AJoaAE0NgKYGQFMDoKkB0NQAaGoANDUAmhoATQ2ApgZAUwOgqQHQ1ABoagA0NQCaGgBNDYCmBkBTA6CpAdDUAGhqADQ1AJoaAE0NgKYGQFMDoKkB0NQAaGoANDUAmhoATQ2ApgZAUwOgqQHQ1ABoagA0NQCaGgBNDYCmBkBTA6CpAdDUAGhqADQ1AJoaAE0NgKYGQFMDoKkB0NQAaGoANDUAmhoATQ2ApgZAUwOgqQHQ1ABoagA0NQCaGgBNDYCmBkBTA6CpAdDUAGhqADQ1AJrO6D99u9vtAsC0vwAAAABJRU5ErkJggg==","cccloud:block/mainframe_leds_online":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAaUlEQVR42mOIvxz/H4TjTjz9r6Wr91+rfiaYDeYD2SuefYXzQeqWvVsGpsFqgZgBphkmCGKDNCFrhBsOxCB1+v36CANgmnEZAFOICzPANMOdheYFogxAdyYpGO4Fsg0gR9OoAaMGUN0AAOhZvrHetwE7AAAAAElFTkSuQmCC","cccloud:block/mainframe_glass":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAGUlEQVR42mOQUlLLowQzjBowasCoAcPFAAAcytABQ4wLmAAAAABJRU5ErkJggg==","cccloud:block/mainframe_leds_connecting":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAWklEQVR42mN4cdT9v5au3n8YDcb1MyEYyI478RTMBsmD8NfHC1DUMmDTDNIE04hsGEztijYthAEwk3EaABPHgeEuwOUFggagaCQDMyD7jSIDKHbBqAGjBlCCAaZvqTblHQZ6AAAAAElFTkSuQmCC","cccloud:block/mainframe_leds_offline":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAS0lEQVR42mO47O//X0tXDwVb2TuCMTIbhEFq3y1Y8B9ZDwMuzegYJg/S3KWlhTAA3QW4NOLCDNgEidWM1QWkYgZKNI8aMGrA4DEAAOY9jx9NPMK/AAAAAElFTkSuQmCC","cccloud:block/relay":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAE40lEQVR42u2ZMWocQRBF+wi+gBOjRBZihUAgEALFAoFP4As4cL4gFOgoTmwQCAW6g3Ofwb6B4zV/4A9PXzVrvDPgYDsoprunVzP169ev6lG7u7vb3N7ebnRdr9cbz22a2zT3nvv7+/F+7uM97Zdxr0x7cq/n3ud7+XteHx8fB3t+fh7HaU9PT+P9h4eHYS7TuNFZPzwfRMcJBEHKcTpUgWiA/GwGwM5nMLxfV+2xQ7raYY7luAGw4wSr8YFVdPmijEQ6d3JyMtjh4eHm9PR0c35+vlmtVsNYa5rrenx8PKzxmYx6BWTFSP++irZBMDheNzAEoyXtiLLnBGTqZeXU0dHRq5dnxGTaI6CSXQSieiYZwb9LpxMAXnOPwWmZ19ucJ/UyUopwtb+i89nZ2YtUqNKsSpkKEEeSNE8wHHVTn+uteqARZlqkSPolDJjoTlpTUzKXDcCUgxX9k6V+fuU0o6/71ARGfwAgqV2pOx1OGvqFlduVU5V+KF2mqk8GJFMowXRknfN0NkUxNeBVFUiEKxX3iyRAEriqklT7BRb/fv5mKj0yNbVmWmekuV6VyhGAfDAfzpeoajmjbGWvKE8WkS3pFMWw0p1kAPsAqn2WwmQBhbHlgxlpz0m/fEFbJWzZ1HhMsP4WfT+raoo0zrqeTmaJTKAaKSwhu7i4GJxRlDxX2dJLa11mujMS2ls1PSmMBiBZlv0AUydByD6gKn3sA1IoaaMIyim/+MHBwWCq2Rz7wQIkU0VAVcKZjRYrRtb8rBpV1Ulwpjo/AkDKe27NGEXQTiXdMpIyg8V9+r1ByraWWqE9AiA7uqoZY4qkmHqclJ7qCFkBXoigkbi+vn6BTJYXoicK+55fRo2Q0kPOCQymjkzrAs7rqS3ZA2w7mxCErOscT1GfLGh28Obm5tWJyZuInNbkiP9QRrs6LNGZjGzVild/p6ouMgbN+c6SWIkhT4jNCCl6jpSi6UOLDzGOpucGJdOjovDfnMhGiYekqfOA51Vksw+o0sDjdnl5OTglanp8dXU1rtF03/c0lk2VHzImRYgR+Prr9+bj95+D7TL+/OPbZvVl/cL+Za3RKTqttcpyD+twCg/Rr87qGs9xXtddHfe4yYnKaTPB9+x4AlE5lufxzEuWrDnOG4A5LGh00E6LFaa8x57nNaPPY2fmX/VRYm4KzKH/CEDlKKNsUDJNDIDpnuUnAai+2y2VAruyoJH+6SCZYSNQ+s22EpOClylhAOawYLYIMq9T8asKYFB8vzp4VA0I+wreWzIFdhJBOlqVQGpCCiZFMFtQCt82MJZIgdkimAJIutuyOnid6m6ns/yxJ8j+fIkqMEsEs+SlswQnU0JWHTmrNpQg8MS2VArsLILM64y8x0l9ApK5X/UA7A2YDks0QouIIIXNjtFpAmJ98DUdrL7FJxjsB5ZMgZ1EsKI1AUiHUxT58YEUZ+/PviC/zy+RArNEMPM8D0akfZZI9gEpePkFpjqxabzkWWAnEST1KYTJBlaILINT39v4sWLqvzRLpcCsTpBRrw5HvJ8syAhnI5Tf6FIz/vtx+O2nD+OCxv4v775Yk+Nv3r8bBWVvATAL9g6AvU+BfXO4A9AB6AB0ADoAHYAOQAegA9AB6AB0ADoAHYAOQAegA9AB6AB0ADoAHYAOQAegA9AB2FP7Ax1QH8Uwxxc2AAAAAElFTkSuQmCC","cccloud:block/relay_status_online":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAMklEQVR42mNY9m7Z//jL8f/1+/X/I7O1dPWIwgzEKsRpAMhGigyg2AWjBowaMGoAGAMANjyLbuo53vkAAAAASUVORK5CYII=","cccloud:block/relay_status_connecting":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAALklEQVR42mN4cdT9/4o2rf9aunr/kdnEYgZSFGM1AGQrRQZQ7IJRA0YNGDUAjAFZtYoDiwn/VQAAAABJRU5ErkJggg==","cccloud:block/relay_status_offline":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAK0lEQVR42mO47O//v0tL67+Wrh5ZmIFcjXADQC6gyACKXTBqwKgBowaAMQB9i4ZB+YIjSQAAAABJRU5ErkJggg==","cccloud:block/relay_status_no_power":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAKElEQVR42mPQ0tX7b2Xv+B8bTQxmIFYhTgNIsY02Lhg1YNSAUQPAGABLiITk8/K5qAAAAABJRU5ErkJggg==","cccloud:block/relay_advanced":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAF7ElEQVR42u2ZvW4lRRCF5xF4ARLkxFiWLUuWLFmWHFuyxBPwAhsgsCbhLRBEK4hIljchJwDvT7gErB0SD1M9fWq+qjsbcGekDW4Hrenp7jvTderUqeq53bs/vx3evO4Hu75+8zD4/eNDbb03Gy9rxv7bv76b5n393GxOY7Z++s201q42N/0+Pme+732N3qk96rm6f3r+cWoffhqenn6e+2N7/ueHaWxsH55elpbXdDKWm5pa3LDAiUDMIM2g9QGQ+MxpjUAUQDZm6zRO4/UOAqr325rn518mw0ZjynU0zsaKkaOxBgINt75+Y9dOm+IG/OVmcG07nhxbMa72z8/PSzs+Ph4uLi6Gq6ur4ezsrPRtzO7tenp6Wsb4Tn8OjdV99bqa3ieQ3CjzdgWhAGAMSECQGWVuHO/cENBKhgp10pRruSkz6uTkZCcs6DFrtsaA4jM0R0DpBOtHRszMkSFuMJhQDMe87gMAOa493mAwPaKNZsqbh3djeqY36Xx5eTmHQg6zx/6jukOH6JnysjysuKfHHRTr17Wa70hzxr4M9U0mkdQmBJjRPYbSQ2RWNdKaAUAxIxBlD0FYoU8IC73fY1weNsNy/Iv2ugcIXaY2FZxXvdCFz0Nl2rDFNj2fhY76MWlAv5N9mA30XP2Oz/DQG/sy1KkOhXdBFDCar7rhIbAjPI8pTWlTFEN6bLyawAVPAYiQXitYfD5/Q13YuQf4Yhop7hlBIVFToGsARbIC1ZH68WUx9uhBgcbwkLIzpimSBMcAYH4nE6LG9CHlBX2q7xK9lQmC6MnT1euuC0iVXX6xAOG95rmZTNcsbLGuiKnOwIoZpl/wdo9nzO9kUSQGFGVHBtC9j4sFVRs8REoarC8yCpuQXV9fF2PMS7q3tGWbtnFrojs9YWtpDNMchVEACCheKZpzzM+6NGvQ/FylNVI75P86T1H0QshF8HECQHQ+OjoqzXI2+/LglMcfwuYNqCXhJBtyxggFFbKGgMpMYGmsd4vaEjV5nSFBIQzl8MSAaVNmlLxKuuX63ZrA4obs91Mh1Mf0mTKCrZkAmEV3zjB9KKAowmQEn+k5ncKnsEjjWutaYRqgPHl3dxdQdAFJpaNdjcJCVpuxQsjCw4wzMBg61mzcgNM402Moc1E3hAyUKkwBL1FjJchan3WBryUD9OP7+/vwIz2MtNKDzRChmr2tTcZyeqa31vKQFKiPdJlPgvPha35ujvdc7gblZ7VYM0MnSpj35Cnzpg4tOsTIm7qX4tJAihQNXUqhNCIXSjwkfew8oHsa5ociOI2FUUiJ1cHdzc1NMcqoqf7t7a2Psdm85qxvLeRanMnFFhedhTLV1r/6+9/h69/fl7ZP/5s/fhvOfv0+tP8z1tEoGm1jSy2vobEOAGgWDicQIoGyxni77mu4+p0ZsWS0mKA5GZ6B4EeGpVgMzEjncbuuMV4ArGFBRwNltLFClFdf9/m688kplZ88irJO19jaEFhDfwdgyVB6WaDkMCkA5AMG08xCVUZBsrZVCOzLgo70zwaSGWoEyjWgepR1uFjBoycPImLA2hBYLYKM66z4SxlAoGg+VFwooGh0TkeeFcbxLUNgLxGkoUspkJqQBVMiGD5B8/NTZQFjPtfsW4TAahHMAki6q+XsoHEpOukevtOhpM4ZwOa2yAKrRDCnvGwswckhoUKInmUhxIyQWSFAtgqBvUWQcZ09r36mPgGRsVnhc3rkPDPE2hDYRAQpbDKMRhMQ6YMXQqy78WWWDOB5nAcSAbBVCOwlgku0JgDZ4CyKPHzkwseNXTgXiAFbhMAqEcxxng9GpH1OkaUOSMIWVB4fH/g5ih8mtjwL7CWCpD6FMLOBGYIMYQm8Q3v+C4OsQMZsFQKrKkF6felwxPnMgvAXFKs+1gdUf4xb++TH4c9ffOUD1te/vIfSOjP8sy+/cEE5WADEgoMD4OBD4NAMbgA0ABoADYAGQAOgAdAAaAA0ABoADYAGQAOgAdAAaAA0ABoADYAGQAOgAdAAaAA0AA60/QftF6XZeNopCQAAAABJRU5ErkJggg==","cccloud:block/relay_ender":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAF7ElEQVR42u2ZvW4lRRCF5xF4ARLkxFiWLUuWLFmWHFuyxBPwAhsgsCbhLRBEK4hIljchJwDvT7gErB0SD1M9fWq+qjsbcGekDW4Hrenp7jvTderUqeq53bs/vx3evO4Hu75+8zD4/eNDbb03Gy9rxv7bv76b5n393GxOY7Z++s201q42N/0+Pme+732N3qk96rm6f3r+cWoffhqenn6e+2N7/ueHaWxsH55elpbXdDKWm5pa3LDAiUDMIM2g9QGQ+MxpjUAUQDZm6zRO4/UOAqr325rn518mw0ZjynU0zsaKkaOxBgINt75+Y9dOm+IG/OVmcG07nhxbMa72z8/PSzs+Ph4uLi6Gq6ur4ezsrPRtzO7tenp6Wsb4Tn8OjdV99bqa3ieQ3CjzdgWhAGAMSECQGWVuHO/cENBKhgp10pRruSkz6uTkZCcs6DFrtsaA4jM0R0DpBOtHRszMkSFuMJhQDMe87gMAOa493mAwPaKNZsqbh3djeqY36Xx5eTmHQg6zx/6jukOH6JnysjysuKfHHRTr17Wa70hzxr4M9U0mkdQmBJjRPYbSQ2RWNdKaAUAxIxBlD0FYoU8IC73fY1weNsNy/Iv2ugcIXaY2FZxXvdCFz0Nl2rDFNj2fhY76MWlAv5N9mA30XP2Oz/DQG/sy1KkOhXdBFDCar7rhIbAjPI8pTWlTFEN6bLyawAVPAYiQXitYfD5/Q13YuQf4Yhop7hlBIVFToGsARbIC1ZH68WUx9uhBgcbwkLIzpimSBMcAYH4nE6LG9CHlBX2q7xK9lQmC6MnT1euuC0iVXX6xAOG95rmZTNcsbLGuiKnOwIoZpl/wdo9nzO9kUSQGFGVHBtC9j4sFVRs8REoarC8yCpuQXV9fF2PMS7q3tGWbtnFrojs9YWtpDNMchVEACCheKZpzzM+6NGvQ/FylNVI75P86T1H0QshF8HECQHQ+OjoqzXI2+/LglMcfwuYNqCXhJBtyxggFFbKGgMpMYGmsd4vaEjV5nSFBIQzl8MSAaVNmlLxKuuX63ZrA4obs91Mh1Mf0mTKCrZkAmEV3zjB9KKAowmQEn+k5ncKnsEjjWutaYRqgPHl3dxdQdAFJpaNdjcJCVpuxQsjCw4wzMBg61mzcgNM402Moc1E3hAyUKkwBL1FjJchan3WBryUD9OP7+/vwIz2MtNKDzRChmr2tTcZyeqa31vKQFKiPdJlPgvPha35ujvdc7gblZ7VYM0MnSpj35Cnzpg4tOsTIm7qX4tJAihQNXUqhNCIXSjwkfew8oHsa5ociOI2FUUiJ1cHdzc1NMcqoqf7t7a2Psdm85qxvLeRanMnFFhedhTLV1r/6+9/h69/fl7ZP/5s/fhvOfv0+tP8z1tEoGm1jSy2vobEOAGgWDicQIoGyxni77mu4+p0ZsWS0mKA5GZ6B4EeGpVgMzEjncbuuMV4ArGFBRwNltLFClFdf9/m688kplZ88irJO19jaEFhDfwdgyVB6WaDkMCkA5AMG08xCVUZBsrZVCOzLgo70zwaSGWoEyjWgepR1uFjBoycPImLA2hBYLYKM66z4SxlAoGg+VFwooGh0TkeeFcbxLUNgLxGkoUspkJqQBVMiGD5B8/NTZQFjPtfsW4TAahHMAki6q+XsoHEpOukevtOhpM4ZwOa2yAKrRDCnvGwswckhoUKInmUhxIyQWSFAtgqBvUWQcZ09r36mPgGRsVnhc3rkPDPE2hDYRAQpbDKMRhMQ6YMXQqy78WWWDOB5nAcSAbBVCOwlgku0JgDZ4CyKPHzkwseNXTgXiAFbhMAqEcxxng9GpH1OkaUOSMIWVB4fH/g5ih8mtjwL7CWCpD6FMLOBGYIMYQm8Q3v+C4OsQMZsFQKrKkF6felwxPnMgvAXFKs+1gdUf4xb++TH4c9ffOUD1te/vIfSOjP8sy+/cEE5WADEgoMD4OBD4NAMbgA0ABoADYAGQAOgAdAAaAA0ABoADYAGQAOgAdAAaAA0ABoADYAGQAOgAdAAaAA0AA60/QftF6XZeNopCQAAAABJRU5ErkJggg==","cccloud:block/uplink":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAFIklEQVR42u2YQUomVxSF/yX0AoxIEGw1ogiCIKJjRcgKsoEMMpeI0K6hBxlnkgZBHLiHnvcaOjvIuMIpOJXvP95SY9VI3+DyXr169VvnvHPPveXi+vq6U1xeXvbjzc3NcH11ddUH1zXynq4957p/T8G9Wuffuru7W4r7+/th/vDw0N3e3vZrDq15XbG2ttatrq52Kysrj0aG1hTe7z0Lvczh4WG3v7/f7ezs9HF0dNQdHBwMofu7u7vDvr29vX5uICaJ1yTMwEm2r5MAgTNYXys0F2DPPRKMARIwQZsMkrDQywqUXuivv//pfvn6/dFpUgG63t7e7kngGp/hs7z273DNAA3aJ2sCUhEcFQRjsI488SRH1wu91Obm5gBe4blPimB8miKNp0+5UwVjKWFlWNYJ1GRQ/iaEZL0U/FgaDAoweEbmL0cpIMGTsIoQrnkfc9syTxUwtE5lJEBK3HOffPpErwC9iHLeJ+sxfzhPuUqBSvJphCTUHkBjI8iUe5KkeZoc46l7gwL0IkoBnhRZ5MgTlgmmN1TeQblzn+eUtecko6oSJIrST/A++fQBHmyfAiKADu5NUsbp6enwIE9TVYF57RRgGaXbpwL8DBWQPsA1egUVQHOrgFZ+sFQGmc8+PT8gAnTSfsDgFC6DlUdU8yyXTIEse0+lAEmiAjK3K8CVQhb6obOzsyXGxx7gC8o4NVZOzxyvDJEE0dRc59MPPGcZpAKqSsA5zTC9YaEfu7i4WGKaJPgH7Mp+QaWA9tLVE3xV9rISVOWPhDDnc137s8RVhOQe+kOvgJOTk6VSNGY4HrWujlHzsTaYJ5/ND72jqgJVOrAC8N2y3c3an93fo0ZIP3x+fr5kMGOGxJNQi6y17BSzzrP/Jym+zjwnyewJSAj7hLF0Tamn/IcUsAeovdV8Y2OjnyvW19f78Fyj/rBGEaAXyO+B7ACrFKEZmtw8bXaBVf33vqreV98D1cfRoAA5uiqBjE3AVBaV4xp1T3L3h5BDFYIekCc/1gnmhxNzf4yE/AiqvgYJOkFWH0dDFbDU+UdYmsYaEkdleM+1xKwQ+SVY9QT0hDTMNLdqXn0UDZ2gTtINj+eu/wqv+/r4+Hhp7adPf3Qff//cj1vX/4XWPPqe53yGH2H8EHvp9W/fvnS7f14uxf9Z6wkQKIFJAgic97TfpBmcQXHOewzumQJe168F7vmCoAyM4AXS67yndYVPluANzkF1+Nr7p4DX+FrgAwEGLjCVCrhONZgASp0ST9nzXipgShpMkf+gAOe0wTLHPVIFBu8UGDt1klClyRweMEX+PQE8UauhAs5UYLrwdFMNBJ/hZ6Z6wGwmaIAGbRKYIkmSPSCl/ZTppV/M4QGTTJB5PyZ5pgnJ0nWWO544za+qDHN5wGQTrIBzztqfSqjAjpFSecRcHvBqE8weIEtelkGqQfcz1wmQck8VzN0HTDJBgkzA6Q30BCugAla5fipkDg+YxQSzxc1W2GOWS3vAWBuc61W6zNkHvLoTpKsneN9Ln/B61QpXDVFWC1/P4QGTTDAlnyUv15kO7gOyGao+kNIA5+oDZjFBGlxGmiKbIKdAuv0YEdXeufqAySbIPiBJYTnMapCtbQJNb8iY6gGTTdA/pv8IKfzfFl//8OvP/Wbt89pbisVzGwT+w9aP/fguCbDRSAnvkoC3Ho2ARkAjoBHQCGgENAIaAY2ARkAjoBHQCGgENAIaAY2ARkAjoBHQCGgENAIaAY2ARkAj4H3Ev9pCPKPcCtPJAAAAAElFTkSuQmCC","cccloud:block/uplink_status_online":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAc0lEQVR42mOIvxz/H4TjTjz9r6Wr91+rfiaYDeYD2SuefYXzQeqWvVsGpsFqgZgBphkmCGKDNCFrhBsOxCB1+v36CANgmnEZAFOICzPANMOdheYFogxAdyYpGO4Fsg0gRxN1DYDFKyx60PlDwAujBlBuAACJxMcbUV/MfwAAAABJRU5ErkJggg==","cccloud:block/uplink_status_connecting":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAZUlEQVR42mN4cdT9v5au3n8YDcb1MyEYyI478RTMBsmD8NfHC1DUMmDTDNIE04hsGEztijYthAEwk3EaABPHgeEuwOUFggagaCQDMyD7jSIDKHYB2QbAAhAWNej8IeCFUQMoNwAAR3ywmMfr1h0AAAAASUVORK5CYII=","cccloud:block/uplink_status_offline":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAT0lEQVR42mO47O//X0tXDwVb2TuCMTIbhEFq3y1Y8B9ZDwMuzegYJg/S3KWlhTAA3QW4NOLCDNgEidWM1QWkYgZKNA8SA9CjZSSGwagB/wF3kJBkBC/1HwAAAABJRU5ErkJggg==","cccloud:block/uplink_advanced":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAF3ElEQVR42u1YsW4kRRScT+ADjGUhS8ZnLFuWLFmyLDu2ZYkv4AcI0LEiQUhI/AECAhBEJPAn5ARwd1wIAbcOiYep3q431bW95vBMdO7gqXu6e2an6r1X7812L5990sOev1j0L3972v/x+8dx/eI59hbpmusY09lnq/3V+mqu66v56tm45lms628t774Ku/v7y2Svlt/1y+X3sbZ89fXKcA7jsHd390Mad3Z2+u3t7X5ra2ttVMMajOd5psPLnJ2d9ScnJ/3h4WGy8/Pz/vT0NAz7R0dHce74+DjNCTIRNQDSayUsSBvOkFyeDbCwAVwCTKAZLNcBurBhTcEQoAJW0CRDSejwsgCFF/rpr3/6D375M3lRvakRgOuDg4NEQlobQNHDvIcjLaIpImxco9cBCGSo90kE1iISZB2jgiFYmnvcycF1B1D7+/sBHsY5PTWmwiK8CdJWIT6G+srDZRTwXo0A7qUUYDgDFOYAOowkIo15T8+m9YGE1wW/KQ06vBzAELxahHcGrqAQAYyOBCyngHqb0TE+Z5wzSpJ3cz6rdxkZoQ88kz3PfQeoIc45Pe86kSIAL4Kcp2c5+oPVyyRA00JtjBqPDBPPYT1yO4tf4XkCpudBjJwFES5yavftRQTgRZAC4wsvChZ1VA9DBF0bFDyjQMNd9YL3UOy0AlAPVBSDHEZJJkVD38HT864D6tiUAiBgzNFFHEJkXF1dxY3qTVQFzWuqO4GSLJ5RHdHU0fwmSFp4WsSPhLASqLjVgNb0oCiDms/0Hm8AAfA0b6C38eJjGVysaQSVXueqB0pUKHwWQAKL0kiTvKf3lQDP7RrgWoR0eMj19fWorMO46YYI1+EeCCfOB8BC6RdFaniFULJY5kIE8/M1AqJKyB7P+3t6FdDo8H4gpQB+5Pb2tmReHswHqBDhBZACOEfP1pogBa46oJWgaHoM4Joe5Pejo1gF7hM6jw7Xhw4Pury8rJYYfTGGKD2DjhHzdeWXRiiD9eYn2uRcBSIK8m9zXlSEfKbYG9a83fXa793fWiMEVm9ubsYwyw9XsMG+9ORokUlAdHhS+0mC9v/RH8i3RpCefz/KXdaDIjq4Rk3IGlBLVw91D/9IATwYGoD2Fg/e29tLc9ju7m4yztOZ4TxGEIC5fw9oszQCLquBfjt47mvI68ePlkSNklq9r30P1D6OUgTgQVB0VAIIG4ChLCLHMWIP4c4PIRoqBF4q1N08r02TtsxaCTD6V6B/CbLx0e+FSJlhVMHb5Pnax1FUAWW5UFypzbXGhC9YCJ6Im7fEWgpHMVyMWiO5rhWp6Aa1KuRzLm61ee2jKDpBeJIND+es/zCu8/ri4qJYe++Lb/t3P/smjU8+Hw1rHLnHud6jH2H6Ifa61x/9+nN/9OOnhf2ftUQAQAGME6DAdQ/nSRrBEZTOdU9Nz0wBj+uHAue8U1AEpuABkuu6h3UYPavgCY6m0cFrnp8CHuNDgQcBBA4wtSjQdY0GEqChriHuYa97HgFT0mBK+EcEMKcJVnOco0YBwTMFNnldSailyRwaMCX8EwHqUUZDDbimgqaLetejQcG78Z6pGjCbCBIgQZMETREniRrgoX2f6LlezKEBk0RQ835TyGuaKFm49nKnHlfxq1WGuTRgsgjWgOtca79HQg3sJlJqGjGXBjxYBL0H8JLnZVCjAfue6wpQw92jYO4+YJIIKkgH7NqgmsAIqAGrqb5HyBwaMIsIeovrrTBHL5fUgE1tsK/X0mXOPuDBnaCquoPnnusE12utcK0h8mrB6zk0YJIIesh7yfN1TQf2Ad4M1T6QXADn6gNmEUEVODcXRW2CmAKu9puIqJ2dqw+YLILaBzgpWg69Gnhr60BdG9ymasBkEeTD8I8QjP/+8vrtD99Ph3GOa2+Sdf91AODfevJOGh8lARQaRMKjJOBNt0ZAI6AR0AhoBDQCGgGNgEZAI6AR0AhoBDQCGgGNgEZAI6AR0AhoBDQCGgGNgEZAI6AR8DjsX0momsxAv340AAAAAElFTkSuQmCC","cccloud:block/me_cloud_bridge":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAADgklEQVR42u2XQUokMRSG6yAiLgQREUUQZES614onmDmJIC7cuZmLCI0wnmOuMbeo4Qt8xd+h0Bm6VjOv4ZFU8pLK/+XldWq4vLwcLy4uxqurq2Y8n52dtTbqx8fH4/X1dWs7Pz8fT09Ppz7aMHxodzz9tDmWdvzocy5t2PG3Xq/bnL4Hy2f7KTX9qA+Ienx8HJ+enlr5/Pzc7OHhYetZH9rtQyBiqWO248cYSq2fh5KxuwJIwSn85uZmqmd7+uEzsHMs5uTkZGvhCnXhtlHqCzwmSgD6Cc8259CXZ8YuAQAhmAIt+x0XSvYN7GLu9Mu3n21nDVkNUF+/fN8SyUT4pkgFzkWFQI0KAC4ZAYZ7ghAOfQlA/wFxLh7xiNzf3x/39vbGg4ODrTp9WArwCCgsBSaIjCD9lgCQOaAP/373MyImAOysi6IDsYhO4YLAEKxIk1qKpI925jIxmgT7PILfrgA8y5kEFZxJsN/5KQmyCEOXcEYkgufEY+YMBJjl+/POzjI+YRppQsCYa1cAc+da8e66fZknbB9yZ1ikO+/i5wAoxNyQIQ8EogTfo6Oj8fDwsM3Fe/JY+L6PxP14+dWO5Z8kwTz/7m5GQZ8XpiSYANg5d73PAcLwb9MQZsLM8NSZE18BUBeU0cZ4xn4kzpz0WQT0Ya7wub/I9G0Abm9vx7e3t2YuPHOAdduZ9PX1dXx/f2+TMH6z2bRn2pkHMP1YfOnD1/ctkQP6Hc4E2F+G8llww/39fVs4xu5muBsJmRBZNL4IIdQZLwDMdm+HlN4gFa//EgD6m93cbis2fYySYbVaTQvzb64/9xp9hKUCEMp4d14zGgSS0UFdCMBZ4l8gd34uGWZ0JIyWBO/u7qYFsihE0oE4do06O4gpXgH0MT6FK9r59BUIz9Y/ywF/cxHq7/09kLmIaAA8w1xvKUlc1E1gPlOnZOGUiCCEHd+LtC2frQthiatwfuT0F6L+zPd/ge39nuFMULlQF9uHMiVJk/HuvLlEE0JGSEbKEh9Dc+HfZ//+WyDLwc9W7//5zBHQ/HzN7wMmse5L8bXPl3N8/Jz2+kz7Ujlg7p7fA9AnP5waABf1v1oBKAAFoAAUgAJQAApAASgABaAAFIACUAAKQAEoAAWgABSAAlAACkABKAAFoAAUgAJQAApAASgABaAAFIACUAAKQAEoAAWgABSAAvAP2m/itLMtJAgWowAAAABJRU5ErkJggg==","cccloud:block/cloud_bridge_status_online":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAARElEQVR42mNgAIIVz77+B2EGcgBIY9yJp2BMliEwzciGgGiSDNGqn/kfGTOMAtLAsnfL/sdfjgdj/X79/+j80RAaEQAABX1EX1eUaJcAAAAASUVORK5CYII=","cccloud:block/cloud_bridge_status_connecting":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAARUlEQVR42mNgAIK4E0//gzADOQCkUat+JhiTZQhMM7IhIJokQ+Tji/8jY4ZRQBp4cdT9PwivaNP6r6Wr9x+dPxpCIwIAADggOjysMcQkAAAAAElFTkSuQmCC","cccloud:block/cloud_bridge_status_offline":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAQElEQVR42mNgAAIre8f/IMxADgBp1NLVA2OyDIFpRjYERJNkiLKa+n9kzDAKSAOX/f3/d2lp/UePDRgeDaERAQDssiZbMOQo8gAAAABJRU5ErkJggg=="}`),models:JSON.parse(`{"mainframe":{"parts":[{"tex":"cccloud:block/mainframe","emit":false,"trans":false,"pos":[16,2,0,0,2,0,0,0,0,16,0,0,0,2,16,16,2,16,16,0,16,0,0,16,0,2,0,0,2,16,0,0,16,0,0,0,16,2,16,16,2,0,16,0,0,16,0,16,0,2,0,16,2,0,16,2,16,0,2,16,0,0,16,16,0,16,16,0,0,0,0,0,13,16,3,3,16,3,3,2,3,13,2,3,3,16,3,3,16,14,3,2,14,3,2,3,13,16,14,13,16,3,13,2,3,13,2,14,2,16,0,0,16,0,0,2,0,2,2,0,0,16,2,2,16,2,2,2,2,0,2,2,0,16,0,0,16,2,0,2,2,0,2,0,2,16,2,2,16,0,2,2,0,2,2,2,0,16,0,2,16,0,2,16,2,0,16,2,16,16,0,14,16,0,14,2,0,16,2,0,14,16,2,16,16,2,16,2,2,14,2,2,14,16,0,14,16,2,14,2,2,14,2,0,16,16,2,16,16,0,16,2,0,16,2,2,14,16,0,16,16,0,16,16,2,14,16,2,2,16,14,0,16,14,0,2,14,2,2,14,0,16,16,2,16,16,2,2,16,0,2,16,0,16,14,0,16,16,0,2,16,0,2,14,2,16,16,2,16,14,2,2,14,2,2,16,0,16,14,2,16,14,2,16,16,0,16,16,16,16,14,14,16,14,14,2,14,16,2,14,14,16,16,16,16,16,16,2,16,14,2,16,14,16,14,14,16,16,14,2,16,14,2,14,16,16,16,16,16,14,16,2,14,16,2,16,14,16,14,16,16,14,16,16,16,14,16,16,3,4,2,1,4,2,1,2,2,3,2,2,1,4,14,3,4,14,3,2,14,1,2,14,1,4,2,1,4,14,1,2,14,1,2,2,3,4,14,3,4,2,3,2,2,3,2,14,1,4,2,3,4,2,3,4,14,1,4,14,1,2,14,3,2,14,3,2,2,1,2,2,3,16,2,1,16,2,1,12,2,3,12,2,1,16,14,3,16,14,3,12,14,1,12,14,1,16,2,1,16,14,1,12,14,1,12,2,3,16,14,3,16,2,3,12,2,3,12,14,1,16,2,3,16,2,3,16,14,1,16,14,1,12,14,3,12,14,3,12,2,1,12,2,3,12,2,1,12,2,1,4,2,3,4,2,1,12,4,3,12,4,3,4,4,1,4,4,1,12,2,1,12,4,1,4,4,1,4,2,3,12,4,3,12,2,3,4,2,3,4,4,1,12,2,3,12,2,3,12,4,1,12,4,1,4,4,3,4,4,3,4,2,1,4,2,3,12,12,1,12,12,1,4,12,3,4,12,1,12,14,3,12,14,3,4,14,1,4,14,1,12,12,1,12,14,1,4,14,1,4,12,3,12,14,3,12,12,3,4,12,3,4,14,1,12,12,3,12,12,3,12,14,1,12,14,1,4,14,3,4,14,3,4,12,1,4,12,1.73,5.65,4,1.73,5.65,12,1.35,4.73,12,1.35,4.73,4,2.65,5.27,12,2.65,5.27,4,2.27,4.35,4,2.27,4.35,12,1.73,5.65,4,2.65,5.27,4,2.65,5.27,12,1.73,5.65,12,1.35,4.73,12,2.27,4.35,12,2.27,4.35,4,1.35,4.73,4,1.73,7.65,4,1.73,7.65,12,1.35,6.73,12,1.35,6.73,4,2.65,7.27,12,2.65,7.27,4,2.27,6.35,4,2.27,6.35,12,1.73,7.65,4,2.65,7.27,4,2.65,7.27,12,1.73,7.65,12,1.35,6.73,12,2.27,6.35,12,2.27,6.35,4,1.35,6.73,4,1.73,9.65,4,1.73,9.65,12,1.35,8.73,12,1.35,8.73,4,2.65,9.27,12,2.65,9.27,4,2.27,8.35,4,2.27,8.35,12,1.73,9.65,4,2.65,9.27,4,2.65,9.27,12,1.73,9.65,12,1.35,8.73,12,2.27,8.35,12,2.27,8.35,4,1.35,8.73,4,1.73,11.65,4,1.73,11.65,12,1.35,10.73,12,1.35,10.73,4,2.65,11.27,12,2.65,11.27,4,2.27,10.35,4,2.27,10.35,12,1.73,11.65,4,2.65,11.27,4,2.65,11.27,12,1.73,11.65,12,1.35,10.73,12,2.27,10.35,12,2.27,10.35,4,1.35,10.73,4,15,4,2,13,4,2,13,2,2,15,2,2,13,4,14,15,4,14,15,2,14,13,2,14,13,4,2,13,4,14,13,2,14,13,2,2,15,4,14,15,4,2,15,2,2,15,2,14,13,4,2,15,4,2,15,4,14,13,4,14,13,2,14,15,2,14,15,2,2,13,2,2,15,16,2,13,16,2,13,12,2,15,12,2,13,16,14,15,16,14,15,12,14,13,12,14,13,16,2,13,16,14,13,12,14,13,12,2,15,16,14,15,16,2,15,12,2,15,12,14,13,16,2,15,16,2,15,16,14,13,16,14,13,12,14,15,12,14,15,12,2,13,12,2,15,12,2,13,12,2,13,4,2,15,4,2,13,12,4,15,12,4,15,4,4,13,4,4,13,12,2,13,12,4,13,4,4,13,4,2,15,12,4,15,12,2,15,4,2,15,4,4,13,12,2,15,12,2,15,12,4,13,12,4,13,4,4,15,4,4,15,4,2,13,4,2,15,12,12,13,12,12,13,4,12,15,4,12,13,12,14,15,12,14,15,4,14,13,4,14,13,12,12,13,12,14,13,4,14,13,4,12,15,12,14,15,12,12,15,4,12,15,4,14,13,12,12,15,12,12,15,12,14,13,12,14,13,4,14,15,4,14,15,4,12,13,4,12,15,12,4,13,12,4,13,11,4,15,11,4,13,12,6,15,12,6,15,11,6,13,11,6,13,12,4,13,12,6,13,11,6,13,11,4,15,12,6,15,12,4,15,11,4,15,11,6,13,12,4,15,12,4,15,12,6,13,12,6,13,11,6,15,11,6,15,11,4,13,11,4,15,12,10,13,12,10,13,11,10,15,11,10,13,12,12,15,12,12,15,11,12,13,11,12,13,12,10,13,12,12,13,11,12,13,11,10,15,12,12,15,12,10,15,11,10,15,11,12,13,12,10,15,12,10,15,12,12,13,12,12,13,11,12,15,11,12,15,11,10,13,11,10,15,11,4,13,11,4,13,10,4,15,10,4,13,11,5,15,11,5,15,10,5,13,10,5,13,11,4,13,11,5,13,10,5,13,10,4,15,11,5,15,11,4,15,10,4,15,10,5,13,11,4,15,11,4,15,11,5,13,11,5,13,10,5,15,10,5,15,10,4,13,10,4,15,11,11,13,11,11,13,10,11,15,10,11,13,11,12,15,11,12,15,10,12,13,10,12,13,11,11,13,11,12,13,10,12,13,10,11,15,11,12,15,11,11,15,10,11,15,10,12,13,11,11,15,11,11,15,11,12,13,11,12,13,10,12,15,10,12,15,10,11,13,10,11,15,5,4,13,5,4,13,4,4,15,4,4,13,5,6,15,5,6,15,4,6,13,4,6,13,5,4,13,5,6,13,4,6,13,4,4,15,5,6,15,5,4,15,4,4,15,4,6,13,5,4,15,5,4,15,5,6,13,5,6,13,4,6,15,4,6,15,4,4,13,4,4,15,5,10,13,5,10,13,4,10,15,4,10,13,5,12,15,5,12,15,4,12,13,4,12,13,5,10,13,5,12,13,4,12,13,4,10,15,5,12,15,5,10,15,4,10,15,4,12,13,5,10,15,5,10,15,5,12,13,5,12,13,4,12,15,4,12,15,4,10,13,4,10,15,6,4,13,6,4,13,5,4,15,5,4,13,6,5,15,6,5,15,5,5,13,5,5,13,6,4,13,6,5,13,5,5,13,5,4,15,6,5,15,6,4,15,5,4,15,5,5,13,6,4,15,6,4,15,6,5,13,6,5,13,5,5,15,5,5,15,5,4,13,5,4,15,6,11,13,6,11,13,5,11,15,5,11,13,6,12,15,6,12,15,5,12,13,5,12,13,6,11,13,6,12,13,5,12,13,5,11,15,6,12,15,6,11,15,5,11,15,5,12,13,6,11,15,6,11,15,6,12,13,6,12,13,5,12,15,5,12,15,5,11,13,5,11,14.75,8.5,4,14.25,8.5,4,14.25,7.5,4,14.75,7.5,4,14.25,8.5,12,14.75,8.5,12,14.75,7.5,12,14.25,7.5,12,14.25,8.5,4,14.25,8.5,12,14.25,7.5,12,14.25,7.5,4,14.75,8.5,12,14.75,8.5,4,14.75,7.5,4,14.75,7.5,12,14.25,8.5,4,14.75,8.5,4,14.75,8.5,12,14.25,8.5,12,14.25,7.5,12,14.75,7.5,12,14.75,7.5,4,14.25,7.5,4,14.7,12,7.5,14.3,12,7.5,14.3,4,7.5,14.7,4,7.5,14.3,12,8.5,14.7,12,8.5,14.7,4,8.5,14.3,4,8.5,14.3,12,7.5,14.3,12,8.5,14.3,4,8.5,14.3,4,7.5,14.7,12,8.5,14.7,12,7.5,14.7,4,7.5,14.7,4,8.5,14.3,12,7.5,14.7,12,7.5,14.7,12,8.5,14.3,12,8.5,14.3,4,8.5,14.7,4,8.5,14.7,4,7.5,14.3,4,7.5,2,16,15,14,16,15,14,2,15,2,2,15,13,3.5,15,3,3.5,15,3,2,15,13,2,15,3,3.5,17.5,13,3.5,17.5,13,2,17.5,3,2,17.5,3,3.5,15,3,3.5,17.5,3,2,17.5,3,2,15,13,3.5,17.5,13,3.5,15,13,2,15,13,2,17.5,3,3.5,15,13,3.5,15,13,3.5,17.5,3,3.5,17.5,3.5,16,17,5.5,16,17,5.5,3.5,17,3.5,3.5,17,3.5,16,15,3.5,16,17,3.5,3.5,17,3.5,3.5,15,5.5,16,17,5.5,16,15,5.5,3.5,15,5.5,3.5,17,7,16,17,9,16,17,9,3.5,17,7,3.5,17,7,16,15,7,16,17,7,3.5,17,7,3.5,15,9,16,17,9,16,15,9,3.5,15,9,3.5,17,10.5,16,17,12.5,16,17,12.5,3.5,17,10.5,3.5,17,10.5,16,15,10.5,16,17,10.5,3.5,17,10.5,3.5,15,12.5,16,17,12.5,16,15,12.5,3.5,15,12.5,3.5,17,3,9,17.25,13,9,17.25,13,8,17.25,3,8,17.25,3,9,15,3,9,17.25,3,8,17.25,3,8,15,13,9,17.25,13,9,15,13,8,15,13,8,17.25,3,9,15,13,9,15,13,9,17.25,3,9,17.25,3,8,17.25,13,8,17.25,13,8,15,3,8,15,3,16,0,2,16,0,2,2,0,3,2,0,2,16,1,3,16,1,3,2,1,2,2,1,2,16,0,2,16,1,2,2,1,2,2,0,3,16,1,3,16,0,3,2,0,3,2,1,2,16,0,3,16,0,3,16,1,2,16,1,14,16,0,13,16,0,13,2,0,14,2,0,13,16,1,14,16,1,14,2,1,13,2,1,13,16,0,13,16,1,13,2,1,13,2,0,14,16,1,14,16,0,14,2,0,14,2,1,13,16,0,14,16,0,14,16,1,13,16,1,13,3,0,3,3,0,3,2,0,13,2,0,3,3,1,13,3,1,13,2,1,3,2,1,3,3,0,3,3,1,3,2,1,3,2,0,13,3,1,13,3,0,13,2,0,13,2,1,3,3,0,13,3,0,13,3,1,3,3,1,13,7,1.5,3,7,1.5,3,3,1.5,13,3,1.5,3,7,1.5,3,7,3,3,3,3,3,3,1.5,13,7,3,13,7,1.5,13,3,1.5,13,3,3,3,7,1.5,13,7,1.5,13,7,3,3,7,3,3,3,3,13,3,3,13,3,1.5,3,3,1.5,13,11.5,1,3,11.5,1,3,7.5,1,13,7.5,1,3,11.5,1,3,11.5,3,3,7.5,3,3,7.5,1,13,11.5,3,13,11.5,1,13,7.5,1,13,7.5,3,3,11.5,1,13,11.5,1,13,11.5,3,3,11.5,3,3,7.5,3,13,7.5,3,13,7.5,1,3,7.5,1,13,16,2,3,16,2,3,12,2,13,12,2,3,16,2,3,16,3,3,12,3,3,12,2,13,16,3,13,16,2,13,12,2,13,12,3,3,16,2,13,16,2,13,16,3,3,16,3,3,12,3,13,12,3,13,12,2,3,12,2,12,16,-0.75,11,16,-0.75,11,9,-0.75,12,9,-0.75,11,16,-0.25,12,16,-0.25,12,9,-0.25,11,9,-0.25,11,16,-0.75,11,16,-0.25,11,9,-0.25,11,9,-0.75,12,16,-0.25,12,16,-0.75,12,9,-0.75,12,9,-0.25,11,16,-0.75,12,16,-0.75,12,16,-0.25,11,16,-0.25,11,9,-0.25,12,9,-0.25,12,9,-0.75,11,9,-0.75,11.25,10.5,-0.25,11.25,10.5,0.5,11.25,10,0.5,11.25,10,-0.25,11.75,10.5,0.5,11.75,10.5,-0.25,11.75,10,-0.25,11.75,10,0.5,11.25,10.5,-0.25,11.75,10.5,-0.25,11.75,10.5,0.5,11.25,10.5,0.5,11.25,10,0.5,11.75,10,0.5,11.75,10,-0.25,11.25,10,-0.25],"uv":[0,0.1094,0.125,0.1094,0.125,0.125,0,0.125,0.375,0.1094,0.5,0.1094,0.5,0.125,0.375,0.125,0.125,0.1094,0.25,0.1094,0.25,0.125,0.125,0.125,0.25,0.1094,0.375,0.1094,0.375,0.125,0.25,0.125,0.125,0.125,0.25,0.125,0.25,0.25,0.125,0.25,0,0.125,0.125,0.125,0.125,0.25,0,0.25,0.5234,0.125,0.6016,0.125,0.6016,0.2344,0.5234,0.2344,0.5234,0.125,0.6094,0.125,0.6094,0.2344,0.5234,0.2344,0.5156,0.125,0.6016,0.125,0.6016,0.2344,0.5156,0.2344,0.1094,0,0.125,0,0.125,0.1094,0.1094,0.1094,0.375,0,0.3906,0,0.3906,0.1094,0.375,0.1094,0.125,0,0.1406,0,0.1406,0.1094,0.125,0.1094,0.3594,0,0.375,0,0.375,0.1094,0.3594,0.1094,0.125,0.125,0.1406,0.125,0.1406,0.1406,0.125,0.1406,0,0,0.0156,0,0.0156,0.1094,0,0.1094,0.4844,0,0.5,0,0.5,0.1094,0.4844,0.1094,0.125,0,0.1406,0,0.1406,0.1094,0.125,0.1094,0.3594,0,0.375,0,0.375,0.1094,0.3594,0.1094,0.2344,0.125,0.25,0.125,0.25,0.1406,0.2344,0.1406,0.1094,0,0.125,0,0.125,0.1094,0.1094,0.1094,0.375,0,0.3906,0,0.3906,0.1094,0.375,0.1094,0.2344,0,0.25,0,0.25,0.1094,0.2344,0.1094,0.25,0,0.2656,0,0.2656,0.1094,0.25,0.1094,0.125,0.2344,0.1406,0.2344,0.1406,0.25,0.125,0.25,0,0,0.0156,0,0.0156,0.1094,0,0.1094,0.4844,0,0.5,0,0.5,0.1094,0.4844,0.1094,0.2344,0,0.25,0,0.25,0.1094,0.2344,0.1094,0.25,0,0.2656,0,0.2656,0.1094,0.25,0.1094,0.2344,0.2344,0.25,0.2344,0.25,0.25,0.2344,0.25,0.1016,0.0938,0.1172,0.0938,0.1172,0.1094,0.1016,0.1094,0.3828,0.0938,0.3984,0.0938,0.3984,0.1094,0.3828,0.1094,0.1406,0.0938,0.2344,0.0938,0.2344,0.1094,0.1406,0.1094,0.5156,0.2188,0.6094,0.2188,0.6094,0.2344,0.5156,0.2344,0.1328,0.1406,0.1484,0.1406,0.1484,0.2344,0.1328,0.2344,0.0078,0.1406,0.0234,0.1406,0.0234,0.2344,0.0078,0.2344,0.1016,0,0.1172,0,0.1172,0.0313,0.1016,0.0313,0.3828,0,0.3984,0,0.3984,0.0313,0.3828,0.0313,0.1406,0,0.2344,0,0.2344,0.0313,0.1406,0.0313,0.5156,0.125,0.6094,0.125,0.6094,0.1563,0.5156,0.1563,0.1328,0.1406,0.1484,0.1406,0.1484,0.2344,0.1328,0.2344,0.0078,0.1406,0.0234,0.1406,0.0234,0.2344,0.0078,0.2344,0.1016,0.0313,0.1172,0.0313,0.1172,0.0938,0.1016,0.0938,0.3828,0.0313,0.3984,0.0313,0.3984,0.0938,0.3828,0.0938,0.1406,0.0313,0.1563,0.0313,0.1563,0.0938,0.1406,0.0938,0.5938,0.1563,0.6094,0.1563,0.6094,0.2188,0.5938,0.2188,0.1328,0.1406,0.1484,0.1406,0.1484,0.1563,0.1328,0.1563,0.0078,0.2188,0.0234,0.2188,0.0234,0.2344,0.0078,0.2344,0.1016,0.0313,0.1172,0.0313,0.1172,0.0938,0.1016,0.0938,0.3828,0.0313,0.3984,0.0313,0.3984,0.0938,0.3828,0.0938,0.2188,0.0313,0.2344,0.0313,0.2344,0.0938,0.2188,0.0938,0.5156,0.1563,0.5313,0.1563,0.5313,0.2188,0.5156,0.2188,0.1328,0.2188,0.1484,0.2188,0.1484,0.2344,0.1328,0.2344,0.0078,0.1406,0.0234,0.1406,0.0234,0.1563,0.0078,0.1563,0.2813,0.207,0.3438,0.207,0.3438,0.2148,0.2813,0.2148,0.1563,0.207,0.2188,0.207,0.2188,0.2148,0.1563,0.2148,0.1367,0.1563,0.1445,0.1563,0.1445,0.2188,0.1367,0.2188,0.1367,0.1563,0.1445,0.1563,0.1445,0.2188,0.1367,0.2188,0.2813,0.1914,0.3438,0.1914,0.3438,0.1992,0.2813,0.1992,0.1563,0.1914,0.2188,0.1914,0.2188,0.1992,0.1563,0.1992,0.1367,0.1563,0.1445,0.1563,0.1445,0.2188,0.1367,0.2188,0.1367,0.1563,0.1445,0.1563,0.1445,0.2188,0.1367,0.2188,0.2813,0.1758,0.3438,0.1758,0.3438,0.1836,0.2813,0.1836,0.1563,0.1758,0.2188,0.1758,0.2188,0.1836,0.1563,0.1836,0.1367,0.1563,0.1445,0.1563,0.1445,0.2188,0.1367,0.2188,0.1367,0.1563,0.1445,0.1563,0.1445,0.2188,0.1367,0.2188,0.2813,0.1602,0.3438,0.1602,0.3438,0.168,0.2813,0.168,0.1563,0.1602,0.2188,0.1602,0.2188,0.168,0.1563,0.168,0.1367,0.1563,0.1445,0.1563,0.1445,0.2188,0.1367,0.2188,0.1367,0.1563,0.1445,0.1563,0.1445,0.2188,0.1367,0.2188,0.0078,0.0938,0.0234,0.0938,0.0234,0.1094,0.0078,0.1094,0.4766,0.0938,0.4922,0.0938,0.4922,0.1094,0.4766,0.1094,0.5156,0.2188,0.6094,0.2188,0.6094,0.2344,0.5156,0.2344,0.2656,0.0938,0.3594,0.0938,0.3594,0.1094,0.2656,0.1094,0.2266,0.1406,0.2422,0.1406,0.2422,0.2344,0.2266,0.2344,0.1016,0.1406,0.1172,0.1406,0.1172,0.2344,0.1016,0.2344,0.0078,0,0.0234,0,0.0234,0.0313,0.0078,0.0313,0.4766,0,0.4922,0,0.4922,0.0313,0.4766,0.0313,0.5156,0.125,0.6094,0.125,0.6094,0.1563,0.5156,0.1563,0.2656,0,0.3594,0,0.3594,0.0313,0.2656,0.0313,0.2266,0.1406,0.2422,0.1406,0.2422,0.2344,0.2266,0.2344,0.1016,0.1406,0.1172,0.1406,0.1172,0.2344,0.1016,0.2344,0.0078,0.0313,0.0234,0.0313,0.0234,0.0938,0.0078,0.0938,0.4766,0.0313,0.4922,0.0313,0.4922,0.0938,0.4766,0.0938,0.5156,0.1563,0.5313,0.1563,0.5313,0.2188,0.5156,0.2188,0.3438,0.0313,0.3594,0.0313,0.3594,0.0938,0.3438,0.0938,0.2266,0.1406,0.2422,0.1406,0.2422,0.1563,0.2266,0.1563,0.1016,0.2188,0.1172,0.2188,0.1172,0.2344,0.1016,0.2344,0.0078,0.0313,0.0234,0.0313,0.0234,0.0938,0.0078,0.0938,0.4766,0.0313,0.4922,0.0313,0.4922,0.0938,0.4766,0.0938,0.5938,0.1563,0.6094,0.1563,0.6094,0.2188,0.5938,0.2188,0.2656,0.0313,0.2813,0.0313,0.2813,0.0938,0.2656,0.0938,0.2266,0.2188,0.2422,0.2188,0.2422,0.2344,0.2266,0.2344,0.1016,0.1406,0.1172,0.1406,0.1172,0.1563,0.1016,0.1563,0.0078,0.0313,0.0234,0.0313,0.0234,0.0391,0.0078,0.0391,0.4766,0.0313,0.4922,0.0313,0.4922,0.0391,0.4766,0.0391,0.5313,0.1563,0.5469,0.1563,0.5469,0.1641,0.5313,0.1641,0.3281,0.0313,0.3438,0.0313,0.3438,0.0391,0.3281,0.0391,0.2266,0.1563,0.2422,0.1563,0.2422,0.1719,0.2266,0.1719,0.1016,0.2031,0.1172,0.2031,0.1172,0.2188,0.1016,0.2188,0.0078,0.0313,0.0234,0.0313,0.0234,0.0391,0.0078,0.0391,0.4766,0.0313,0.4922,0.0313,0.4922,0.0391,0.4766,0.0391,0.5781,0.1563,0.5938,0.1563,0.5938,0.1641,0.5781,0.1641,0.2813,0.0313,0.2969,0.0313,0.2969,0.0391,0.2813,0.0391,0.2266,0.2031,0.2422,0.2031,0.2422,0.2188,0.2266,0.2188,0.1016,0.1563,0.1172,0.1563,0.1172,0.1719,0.1016,0.1719,0.0078,0.0391,0.0234,0.0391,0.0234,0.0469,0.0078,0.0469,0.4766,0.0391,0.4922,0.0391,0.4922,0.0469,0.4766,0.0469,0.5313,0.1641,0.5391,0.1641,0.5391,0.1719,0.5313,0.1719,0.3359,0.0391,0.3438,0.0391,0.3438,0.0469,0.3359,0.0469,0.2266,0.1563,0.2422,0.1563,0.2422,0.1641,0.2266,0.1641,0.1016,0.2109,0.1172,0.2109,0.1172,0.2188,0.1016,0.2188,0.0078,0.0391,0.0234,0.0391,0.0234,0.0469,0.0078,0.0469,0.4766,0.0391,0.4922,0.0391,0.4922,0.0469,0.4766,0.0469,0.5859,0.1641,0.5938,0.1641,0.5938,0.1719,0.5859,0.1719,0.2813,0.0391,0.2891,0.0391,0.2891,0.0469,0.2813,0.0469,0.2266,0.2109,0.2422,0.2109,0.2422,0.2188,0.2266,0.2188,0.1016,0.1563,0.1172,0.1563,0.1172,0.1641,0.1016,0.1641,0.0078,0.0859,0.0234,0.0859,0.0234,0.0938,0.0078,0.0938,0.4766,0.0859,0.4922,0.0859,0.4922,0.0938,0.4766,0.0938,0.5313,0.2109,0.5469,0.2109,0.5469,0.2188,0.5313,0.2188,0.3281,0.0859,0.3438,0.0859,0.3438,0.0938,0.3281,0.0938,0.2266,0.1563,0.2422,0.1563,0.2422,0.1719,0.2266,0.1719,0.1016,0.2031,0.1172,0.2031,0.1172,0.2188,0.1016,0.2188,0.0078,0.0859,0.0234,0.0859,0.0234,0.0938,0.0078,0.0938,0.4766,0.0859,0.4922,0.0859,0.4922,0.0938,0.4766,0.0938,0.5781,0.2109,0.5938,0.2109,0.5938,0.2188,0.5781,0.2188,0.2813,0.0859,0.2969,0.0859,0.2969,0.0938,0.2813,0.0938,0.2266,0.2031,0.2422,0.2031,0.2422,0.2188,0.2266,0.2188,0.1016,0.1563,0.1172,0.1563,0.1172,0.1719,0.1016,0.1719,0.0078,0.0781,0.0234,0.0781,0.0234,0.0859,0.0078,0.0859,0.4766,0.0781,0.4922,0.0781,0.4922,0.0859,0.4766,0.0859,0.5313,0.2031,0.5391,0.2031,0.5391,0.2109,0.5313,0.2109,0.3359,0.0781,0.3438,0.0781,0.3438,0.0859,0.3359,0.0859,0.2266,0.1563,0.2422,0.1563,0.2422,0.1641,0.2266,0.1641,0.1016,0.2109,0.1172,0.2109,0.1172,0.2188,0.1016,0.2188,0.0078,0.0781,0.0234,0.0781,0.0234,0.0859,0.0078,0.0859,0.4766,0.0781,0.4922,0.0781,0.4922,0.0859,0.4766,0.0859,0.5859,0.2031,0.5938,0.2031,0.5938,0.2109,0.5859,0.2109,0.2813,0.0781,0.2891,0.0781,0.2891,0.0859,0.2813,0.0859,0.2266,0.2109,0.2422,0.2109,0.2422,0.2188,0.2266,0.2188,0.1016,0.1563,0.1172,0.1563,0.1172,0.1641,0.1016,0.1641,0.3848,0.1836,0.3887,0.1836,0.3887,0.1914,0.3848,0.1914,0.4863,0.1836,0.4902,0.1836,0.4902,0.1914,0.4863,0.1914,0.4063,0.1836,0.4688,0.1836,0.4688,0.1914,0.4063,0.1914,0.4063,0.1836,0.4688,0.1836,0.4688,0.1914,0.4063,0.1914,0.4863,0.1563,0.4902,0.1563,0.4902,0.2188,0.4863,0.2188,0.4863,0.1563,0.4902,0.1563,0.4902,0.2188,0.4863,0.2188,0.3852,0.1563,0.3883,0.1563,0.3883,0.2188,0.3852,0.2188,0.4867,0.1563,0.4898,0.1563,0.4898,0.2188,0.4867,0.2188,0.4336,0.1563,0.4414,0.1563,0.4414,0.2188,0.4336,0.2188,0.4336,0.1563,0.4414,0.1563,0.4414,0.2188,0.4336,0.2188,0.4867,0.1836,0.4898,0.1836,0.4898,0.1914,0.4867,0.1914,0.4867,0.1836,0.4898,0.1836,0.4898,0.1914,0.4867,0.1914,0.3906,0,0.4844,0,0.4844,0.1094,0.3906,0.1094,0.3984,0.2227,0.4766,0.2227,0.4766,0.2344,0.3984,0.2344,0.3984,0.2227,0.4766,0.2227,0.4766,0.2344,0.3984,0.2344,0.375,0.2227,0.3867,0.2227,0.3867,0.2344,0.375,0.2344,0.375,0.2227,0.3828,0.2227,0.3828,0.2344,0.375,0.2344,0.3984,0.125,0.4766,0.125,0.4766,0.1367,0.3984,0.1367,0.9023,0.125,0.918,0.125,0.918,0.2227,0.9023,0.2227,0.875,0.125,0.8828,0.125,0.8828,0.2227,0.875,0.2227,0.875,0.125,0.8828,0.125,0.8828,0.2227,0.875,0.2227,0.9297,0.125,0.9453,0.125,0.9453,0.2227,0.9297,0.2227,0.875,0.125,0.8828,0.125,0.8828,0.2227,0.875,0.2227,0.875,0.125,0.8828,0.125,0.8828,0.2227,0.875,0.2227,0.957,0.125,0.9727,0.125,0.9727,0.2227,0.957,0.2227,0.875,0.125,0.8828,0.125,0.8828,0.2227,0.875,0.2227,0.875,0.125,0.8828,0.125,0.8828,0.2227,0.875,0.2227,0.3984,0.1797,0.4766,0.1797,0.4766,0.1875,0.3984,0.1875,0.375,0.1797,0.3848,0.1797,0.3848,0.1875,0.375,0.1875,0.375,0.1797,0.3828,0.1797,0.3828,0.1875,0.375,0.1875,0.3984,0.125,0.4766,0.125,0.4766,0.1348,0.3984,0.1348,0.3984,0.125,0.4766,0.125,0.4766,0.1328,0.3984,0.1328,0.4766,0.125,0.4844,0.125,0.4844,0.2344,0.4766,0.2344,0.3906,0.125,0.3984,0.125,0.3984,0.2344,0.3906,0.2344,0.375,0.125,0.3828,0.125,0.3828,0.2344,0.375,0.2344,0.4922,0.125,0.5,0.125,0.5,0.2344,0.4922,0.2344,0.3906,0.125,0.3984,0.125,0.3984,0.1328,0.3906,0.1328,0.3906,0.125,0.3984,0.125,0.3984,0.2344,0.3906,0.2344,0.4766,0.125,0.4844,0.125,0.4844,0.2344,0.4766,0.2344,0.375,0.125,0.3828,0.125,0.3828,0.2344,0.375,0.2344,0.4922,0.125,0.5,0.125,0.5,0.2344,0.4922,0.2344,0.4766,0.125,0.4844,0.125,0.4844,0.1328,0.4766,0.1328,0.3984,0.2266,0.4766,0.2266,0.4766,0.2344,0.3984,0.2344,0.3984,0.2266,0.4766,0.2266,0.4766,0.2344,0.3984,0.2344,0.375,0.2266,0.3828,0.2266,0.3828,0.2344,0.375,0.2344,0.4922,0.2266,0.5,0.2266,0.5,0.2344,0.4922,0.2344,0.3984,0.125,0.4766,0.125,0.4766,0.1328,0.3984,0.1328,0.125,0.25,0.2031,0.25,0.2031,0.2813,0.125,0.2813,0.3867,0.1953,0.3984,0.1953,0.3984,0.2266,0.3867,0.2266,0.4766,0.1953,0.4883,0.1953,0.4883,0.2266,0.4766,0.2266,0.3984,0.1367,0.4766,0.1367,0.4766,0.1484,0.3984,0.1484,0.3984,0.2266,0.4766,0.2266,0.4766,0.2383,0.3984,0.2383,0.125,0.2813,0.2031,0.2813,0.2031,0.3125,0.125,0.3125,0.3828,0.1602,0.3984,0.1602,0.3984,0.1914,0.3828,0.1914,0.4766,0.1602,0.4922,0.1602,0.4922,0.1914,0.4766,0.1914,0.3984,0.1328,0.4766,0.1328,0.4766,0.1484,0.3984,0.1484,0.3984,0.2266,0.4766,0.2266,0.4766,0.2422,0.3984,0.2422,0.125,0.3125,0.2031,0.3125,0.2031,0.3438,0.125,0.3438,0.3906,0.125,0.3984,0.125,0.3984,0.1563,0.3906,0.1563,0.4766,0.125,0.4844,0.125,0.4844,0.1563,0.4766,0.1563,0.3984,0.1406,0.4766,0.1406,0.4766,0.1484,0.3984,0.1484,0.3984,0.2266,0.4766,0.2266,0.4766,0.2344,0.3984,0.2344,0.2813,0.125,0.2891,0.125,0.2891,0.1797,0.2813,0.1797,0.3359,0.125,0.3438,0.125,0.3438,0.1797,0.3359,0.1797,0.3691,0.125,0.3731,0.125,0.3731,0.1797,0.3691,0.1797,0.252,0.125,0.2559,0.125,0.2559,0.1797,0.252,0.1797,0.3359,0.2441,0.3438,0.2441,0.3438,0.2481,0.3359,0.2481,0.3359,0.127,0.3438,0.127,0.3438,0.1309,0.3359,0.1309,0.125,0.168,0.1289,0.168,0.1289,0.1719,0.125,0.1719,0.125,0.168,0.127,0.168,0.127,0.1719,0.125,0.1719,0.2129,0.125,0.2168,0.125,0.2168,0.1289,0.2129,0.1289,0.2129,0.125,0.2168,0.125,0.2168,0.127,0.2129,0.127],"shade":[0.8,0.8,0.6,0.6,1,0.5,0.8,0.6,0.6,0.8,0.8,0.6,0.6,1,0.8,0.8,0.6,0.6,1,0.8,0.8,0.6,0.6,1,0.8,0.8,0.6,0.6,1,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.66,0.59,0.94,0.51,0.66,0.59,0.94,0.51,0.66,0.59,0.94,0.51,0.66,0.59,0.94,0.51,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.8,0.6,0.6,1,0.8,0.6,0.6,0.8,0.6,0.6,0.8,0.6,0.6,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.8,0.8,0.6,0.6,1,0.8,0.8,0.6,0.6,1,0.8,0.6,0.6,1,0.5,0.8,0.6,0.6,1,0.5,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.6,0.6,1,0.5],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22,24,26,25,24,27,26,28,30,29,28,31,30,32,34,33,32,35,34,36,38,37,36,39,38,40,42,41,40,43,42,44,46,45,44,47,46,48,50,49,48,51,50,52,54,53,52,55,54,56,58,57,56,59,58,60,62,61,60,63,62,64,66,65,64,67,66,68,70,69,68,71,70,72,74,73,72,75,74,76,78,77,76,79,78,80,82,81,80,83,82,84,86,85,84,87,86,88,90,89,88,91,90,92,94,93,92,95,94,96,98,97,96,99,98,100,102,101,100,103,102,104,106,105,104,107,106,108,110,109,108,111,110,112,114,113,112,115,114,116,118,117,116,119,118,120,122,121,120,123,122,124,126,125,124,127,126,128,130,129,128,131,130,132,134,133,132,135,134,136,138,137,136,139,138,140,142,141,140,143,142,144,146,145,144,147,146,148,150,149,148,151,150,152,154,153,152,155,154,156,158,157,156,159,158,160,162,161,160,163,162,164,166,165,164,167,166,168,170,169,168,171,170,172,174,173,172,175,174,176,178,177,176,179,178,180,182,181,180,183,182,184,186,185,184,187,186,188,190,189,188,191,190,192,194,193,192,195,194,196,198,197,196,199,198,200,202,201,200,203,202,204,206,205,204,207,206,208,210,209,208,211,210,212,214,213,212,215,214,216,218,217,216,219,218,220,222,221,220,223,222,224,226,225,224,227,226,228,230,229,228,231,230,232,234,233,232,235,234,236,238,237,236,239,238,240,242,241,240,243,242,244,246,245,244,247,246,248,250,249,248,251,250,252,254,253,252,255,254,256,258,257,256,259,258,260,262,261,260,263,262,264,266,265,264,267,266,268,270,269,268,271,270,272,274,273,272,275,274,276,278,277,276,279,278,280,282,281,280,283,282,284,286,285,284,287,286,288,290,289,288,291,290,292,294,293,292,295,294,296,298,297,296,299,298,300,302,301,300,303,302,304,306,305,304,307,306,308,310,309,308,311,310,312,314,313,312,315,314,316,318,317,316,319,318,320,322,321,320,323,322,324,326,325,324,327,326,328,330,329,328,331,330,332,334,333,332,335,334,336,338,337,336,339,338,340,342,341,340,343,342,344,346,345,344,347,346,348,350,349,348,351,350,352,354,353,352,355,354,356,358,357,356,359,358,360,362,361,360,363,362,364,366,365,364,367,366,368,370,369,368,371,370,372,374,373,372,375,374,376,378,377,376,379,378,380,382,381,380,383,382,384,386,385,384,387,386,388,390,389,388,391,390,392,394,393,392,395,394,396,398,397,396,399,398,400,402,401,400,403,402,404,406,405,404,407,406,408,410,409,408,411,410,412,414,413,412,415,414,416,418,417,416,419,418,420,422,421,420,423,422,424,426,425,424,427,426,428,430,429,428,431,430,432,434,433,432,435,434,436,438,437,436,439,438,440,442,441,440,443,442,444,446,445,444,447,446,448,450,449,448,451,450,452,454,453,452,455,454,456,458,457,456,459,458,460,462,461,460,463,462,464,466,465,464,467,466,468,470,469,468,471,470,472,474,473,472,475,474,476,478,477,476,479,478,480,482,481,480,483,482,484,486,485,484,487,486,488,490,489,488,491,490,492,494,493,492,495,494,496,498,497,496,499,498,500,502,501,500,503,502,504,506,505,504,507,506,508,510,509,508,511,510,512,514,513,512,515,514,516,518,517,516,519,518,520,522,521,520,523,522,524,526,525,524,527,526,528,530,529,528,531,530,532,534,533,532,535,534,536,538,537,536,539,538,540,542,541,540,543,542,544,546,545,544,547,546,548,550,549,548,551,550,552,554,553,552,555,554,556,558,557,556,559,558,560,562,561,560,563,562,564,566,565,564,567,566,568,570,569,568,571,570,572,574,573,572,575,574,576,578,577,576,579,578,580,582,581,580,583,582,584,586,585,584,587,586,588,590,589,588,591,590,592,594,593,592,595,594,596,598,597,596,599,598,600,602,601,600,603,602,604,606,605,604,607,606,608,610,609,608,611,610,612,614,613,612,615,614,616,618,617,616,619,618,620,622,621,620,623,622,624,626,625,624,627,626,628,630,629,628,631,630,632,634,633,632,635,634,636,638,637,636,639,638,640,642,641,640,643,642,644,646,645,644,647,646,648,650,649,648,651,650,652,654,653,652,655,654,656,658,657,656,659,658,660,662,661,660,663,662,664,666,665,664,667,666,668,670,669,668,671,670,672,674,673,672,675,674,676,678,677,676,679,678,680,682,681,680,683,682,684,686,685,684,687,686,688,690,689,688,691,690,692,694,693,692,695,694,696,698,697,696,699,698,700,702,701,700,703,702,704,706,705,704,707,706,708,710,709,708,711,710,712,714,713,712,715,714,716,718,717,716,719,718,720,722,721,720,723,722,724,726,725,724,727,726,728,730,729,728,731,730,732,734,733,732,735,734,736,738,737,736,739,738,740,742,741,740,743,742,744,746,745,744,747,746,748,750,749,748,751,750,752,754,753,752,755,754,756,758,757,756,759,758,760,762,761,760,763,762,764,766,765,764,767,766,768,770,769,768,771,770,772,774,773,772,775,774,776,778,777,776,779,778,780,782,781,780,783,782,784,786,785,784,787,786,788,790,789,788,791,790,792,794,793,792,795,794,796,798,797,796,799,798,800,802,801,800,803,802,804,806,805,804,807,806,808,810,809,808,811,810,812,814,813,812,815,814,816,818,817,816,819,818,820,822,821,820,823,822,824,826,825,824,827,826,828,830,829,828,831,830,832,834,833,832,835,834,836,838,837,836,839,838,840,842,841,840,843,842,844,846,845,844,847,846,848,850,849,848,851,850]},{"tex":"cccloud:block/mainframe_leds_online","emit":true,"trans":false,"pos":[7,6,1.25,4,6,1.25,4,5,1.25,7,5,1.25,4,6,1.25,4,6,1.5,4,5,1.5,4,5,1.25,7,6,1.5,7,6,1.25,7,5,1.25,7,5,1.5,4,6,1.25,7,6,1.25,7,6,1.5,4,6,1.5,4,5,1.5,7,5,1.5,7,5,1.25,4,5,1.25,7,10.5,0.75,4,10.5,0.75,4,9.5,0.75,7,9.5,0.75,4,10.5,0.75,4,10.5,1,4,9.5,1,4,9.5,0.75,7,10.5,1,7,10.5,0.75,7,9.5,0.75,7,9.5,1,4,10.5,0.75,7,10.5,0.75,7,10.5,1,4,10.5,1,4,9.5,1,7,9.5,1,7,9.5,0.75,4,9.5,0.75,7,15,1.75,4,15,1.75,4,14,1.75,7,14,1.75,4,15,1.75,4,15,2,4,14,2,4,14,1.75,7,15,2,7,15,1.75,7,14,1.75,7,14,2,4,15,1.75,7,15,1.75,7,15,2,4,15,2,4,14,2,7,14,2,7,14,1.75,4,14,1.75],"uv":[0,0,0.1875,0,0.1875,0.0625,0,0.0625,0,0,0.1875,0,0.1875,0.0625,0,0.0625,0,0,0.1875,0,0.1875,0.0625,0,0.0625,0,0,0.1875,0,0.1875,0.0625,0,0.0625,0,0,0.1875,0,0.1875,0.0625,0,0.0625,0,0.0625,0.1875,0.0625,0.1875,0.125,0,0.125,0,0.0625,0.1875,0.0625,0.1875,0.125,0,0.125,0,0.0625,0.1875,0.0625,0.1875,0.125,0,0.125,0,0.0625,0.1875,0.0625,0.1875,0.125,0,0.125,0,0.0625,0.1875,0.0625,0.1875,0.125,0,0.125,0,0.125,0.1875,0.125,0.1875,0.1875,0,0.1875,0,0.125,0.1875,0.125,0.1875,0.1875,0,0.1875,0,0.125,0.1875,0.125,0.1875,0.1875,0,0.1875,0,0.125,0.1875,0.125,0.1875,0.1875,0,0.1875,0,0.125,0.1875,0.125,0.1875,0.1875,0,0.1875],"shade":[1,1,1,1,1,1,1,1,1,1,1,1,1,1,1],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22,24,26,25,24,27,26,28,30,29,28,31,30,32,34,33,32,35,34,36,38,37,36,39,38,40,42,41,40,43,42,44,46,45,44,47,46,48,50,49,48,51,50,52,54,53,52,55,54,56,58,57,56,59,58]},{"tex":"cccloud:block/mainframe_glass","emit":false,"trans":true,"pos":[13,16,0.5,3,16,0.5,3,3,0.5,13,3,0.5],"uv":[0.1875,0,0.8125,0,0.8125,0.8125,0.1875,0.8125],"shade":[0.8],"idx":[0,2,1,0,3,2]},{"tex":"cccloud:block/mainframe","emit":false,"trans":false,"pos":[16,32,0,0,32,0,0,30,0,16,30,0,0,32,16,16,32,16,16,30,16,0,30,16,0,32,0,0,32,16,0,30,16,0,30,0,16,32,16,16,32,0,16,30,0,16,30,16,0,32,0,16,32,0,16,32,16,0,32,16,0,30,16,16,30,16,16,30,0,0,30,0,13,30,3,3,30,3,3,16,3,13,16,3,3,30,3,3,30,14,3,16,14,3,16,3,13,30,14,13,30,3,13,16,3,13,16,14,2,30,0,0,30,0,0,16,0,2,16,0,0,30,2,2,30,2,2,16,2,0,16,2,0,30,0,0,30,2,0,16,2,0,16,0,2,30,2,2,30,0,2,16,0,2,16,2,0,16,2,2,16,2,2,16,0,0,16,0,16,30,0,14,30,0,14,16,0,16,16,0,14,30,2,16,30,2,16,16,2,14,16,2,14,30,0,14,30,2,14,16,2,14,16,0,16,30,2,16,30,0,16,16,0,16,16,2,14,16,2,16,16,2,16,16,0,14,16,0,2,30,14,0,30,14,0,16,14,2,16,14,0,30,16,2,30,16,2,16,16,0,16,16,0,30,14,0,30,16,0,16,16,0,16,14,2,30,16,2,30,14,2,16,14,2,16,16,0,16,16,2,16,16,2,16,14,0,16,14,16,30,14,14,30,14,14,16,14,16,16,14,14,30,16,16,30,16,16,16,16,14,16,16,14,30,14,14,30,16,14,16,16,14,16,14,16,30,16,16,30,14,16,16,14,16,16,16,14,16,16,16,16,16,16,16,14,14,16,14,3,19,2,1,19,2,1,16,2,3,16,2,1,19,14,3,19,14,3,16,14,1,16,14,1,19,2,1,19,14,1,16,14,1,16,2,3,19,14,3,19,2,3,16,2,3,16,14,1,19,2,3,19,2,3,19,14,1,19,14,1,16,14,3,16,14,3,16,2,1,16,2,3,30,2,1,30,2,1,27,2,3,27,2,1,30,14,3,30,14,3,27,14,1,27,14,1,30,2,1,30,14,1,27,14,1,27,2,3,30,14,3,30,2,3,27,2,3,27,14,1,30,2,3,30,2,3,30,14,1,30,14,1,27,14,3,27,14,3,27,2,1,27,2,3,27,2,1,27,2,1,19,2,3,19,2,1,27,4,3,27,4,3,19,4,1,19,4,1,27,2,1,27,4,1,19,4,1,19,2,3,27,4,3,27,2,3,19,2,3,19,4,1,27,2,3,27,2,3,27,4,1,27,4,1,19,4,3,19,4,3,19,2,1,19,2,3,27,12,1,27,12,1,19,12,3,19,12,1,27,14,3,27,14,3,19,14,1,19,14,1,27,12,1,27,14,1,19,14,1,19,12,3,27,14,3,27,12,3,19,12,3,19,14,1,27,12,3,27,12,3,27,14,1,27,14,1,19,14,3,19,14,3,19,12,1,19,12,1.73,20.65,4,1.73,20.65,12,1.35,19.73,12,1.35,19.73,4,2.65,20.27,12,2.65,20.27,4,2.27,19.35,4,2.27,19.35,12,1.73,20.65,4,2.65,20.27,4,2.65,20.27,12,1.73,20.65,12,1.35,19.73,12,2.27,19.35,12,2.27,19.35,4,1.35,19.73,4,1.73,22.65,4,1.73,22.65,12,1.35,21.73,12,1.35,21.73,4,2.65,22.27,12,2.65,22.27,4,2.27,21.35,4,2.27,21.35,12,1.73,22.65,4,2.65,22.27,4,2.65,22.27,12,1.73,22.65,12,1.35,21.73,12,2.27,21.35,12,2.27,21.35,4,1.35,21.73,4,1.73,24.65,4,1.73,24.65,12,1.35,23.73,12,1.35,23.73,4,2.65,24.27,12,2.65,24.27,4,2.27,23.35,4,2.27,23.35,12,1.73,24.65,4,2.65,24.27,4,2.65,24.27,12,1.73,24.65,12,1.35,23.73,12,2.27,23.35,12,2.27,23.35,4,1.35,23.73,4,1.73,26.65,4,1.73,26.65,12,1.35,25.73,12,1.35,25.73,4,2.65,26.27,12,2.65,26.27,4,2.27,25.35,4,2.27,25.35,12,1.73,26.65,4,2.65,26.27,4,2.65,26.27,12,1.73,26.65,12,1.35,25.73,12,2.27,25.35,12,2.27,25.35,4,1.35,25.73,4,15,19,2,13,19,2,13,16,2,15,16,2,13,19,14,15,19,14,15,16,14,13,16,14,13,19,2,13,19,14,13,16,14,13,16,2,15,19,14,15,19,2,15,16,2,15,16,14,13,19,2,15,19,2,15,19,14,13,19,14,13,16,14,15,16,14,15,16,2,13,16,2,15,30,2,13,30,2,13,27,2,15,27,2,13,30,14,15,30,14,15,27,14,13,27,14,13,30,2,13,30,14,13,27,14,13,27,2,15,30,14,15,30,2,15,27,2,15,27,14,13,30,2,15,30,2,15,30,14,13,30,14,13,27,14,15,27,14,15,27,2,13,27,2,15,27,2,13,27,2,13,19,2,15,19,2,13,27,4,15,27,4,15,19,4,13,19,4,13,27,2,13,27,4,13,19,4,13,19,2,15,27,4,15,27,2,15,19,2,15,19,4,13,27,2,15,27,2,15,27,4,13,27,4,13,19,4,15,19,4,15,19,2,13,19,2,15,27,12,13,27,12,13,19,12,15,19,12,13,27,14,15,27,14,15,19,14,13,19,14,13,27,12,13,27,14,13,19,14,13,19,12,15,27,14,15,27,12,15,19,12,15,19,14,13,27,12,15,27,12,15,27,14,13,27,14,13,19,14,15,19,14,15,19,12,13,19,12,13.35,20.27,4,13.35,20.27,12,13.73,19.35,12,13.73,19.35,4,14.27,20.65,12,14.27,20.65,4,14.65,19.73,4,14.65,19.73,12,13.35,20.27,4,14.27,20.65,4,14.27,20.65,12,13.35,20.27,12,13.73,19.35,12,14.65,19.73,12,14.65,19.73,4,13.73,19.35,4,13.35,22.27,4,13.35,22.27,12,13.73,21.35,12,13.73,21.35,4,14.27,22.65,12,14.27,22.65,4,14.65,21.73,4,14.65,21.73,12,13.35,22.27,4,14.27,22.65,4,14.27,22.65,12,13.35,22.27,12,13.73,21.35,12,14.65,21.73,12,14.65,21.73,4,13.73,21.35,4,13.35,24.27,4,13.35,24.27,12,13.73,23.35,12,13.73,23.35,4,14.27,24.65,12,14.27,24.65,4,14.65,23.73,4,14.65,23.73,12,13.35,24.27,4,14.27,24.65,4,14.27,24.65,12,13.35,24.27,12,13.73,23.35,12,14.65,23.73,12,14.65,23.73,4,13.73,23.35,4,13.35,26.27,4,13.35,26.27,12,13.73,25.35,12,13.73,25.35,4,14.27,26.65,12,14.27,26.65,4,14.65,25.73,4,14.65,25.73,12,13.35,26.27,4,14.27,26.65,4,14.27,26.65,12,13.35,26.27,12,13.73,25.35,12,14.65,25.73,12,14.65,25.73,4,13.73,25.35,4,2,30,15,14,30,15,14,16,15,2,16,15,3,28,15.5,13,28,15.5,13,21,15.5,3,21,15.5,3,28,15,3,28,15.5,3,21,15.5,3,21,15,13,28,15.5,13,28,15,13,21,15,13,21,15.5,3,28,15,13,28,15,13,28,15.5,3,28,15.5,3,21,15.5,13,21,15.5,13,21,15,3,21,15,3.5,23,17,5.5,23,17,5.5,16,17,3.5,16,17,3.5,23,15,3.5,23,17,3.5,16,17,3.5,16,15,5.5,23,17,5.5,23,15,5.5,16,15,5.5,16,17,3.25,25.5,17.25,5.75,25.5,17.25,5.75,23,17.25,3.25,23,17.25,3.25,25.5,15.5,3.25,25.5,17.25,3.25,23,17.25,3.25,23,15.5,5.75,25.5,17.25,5.75,25.5,15.5,5.75,23,15.5,5.75,23,17.25,3.25,25.5,15.5,5.75,25.5,15.5,5.75,25.5,17.25,3.25,25.5,17.25,3.25,23,17.25,5.75,23,17.25,5.75,23,15.5,3.25,23,15.5,7,23,17,9,23,17,9,16,17,7,16,17,7,23,15,7,23,17,7,16,17,7,16,15,9,23,17,9,23,15,9,16,15,9,16,17,6.75,25.5,17.25,9.25,25.5,17.25,9.25,23,17.25,6.75,23,17.25,6.75,25.5,15.5,6.75,25.5,17.25,6.75,23,17.25,6.75,23,15.5,9.25,25.5,17.25,9.25,25.5,15.5,9.25,23,15.5,9.25,23,17.25,6.75,25.5,15.5,9.25,25.5,15.5,9.25,25.5,17.25,6.75,25.5,17.25,6.75,23,17.25,9.25,23,17.25,9.25,23,15.5,6.75,23,15.5,10.5,23,17,12.5,23,17,12.5,16,17,10.5,16,17,10.5,23,15,10.5,23,17,10.5,16,17,10.5,16,15,12.5,23,17,12.5,23,15,12.5,16,15,12.5,16,17,10.25,25.5,17.25,12.75,25.5,17.25,12.75,23,17.25,10.25,23,17.25,10.25,25.5,15.5,10.25,25.5,17.25,10.25,23,17.25,10.25,23,15.5,12.75,25.5,17.25,12.75,25.5,15.5,12.75,23,15.5,12.75,23,17.25,10.25,25.5,15.5,12.75,25.5,15.5,12.75,25.5,17.25,10.25,25.5,17.25,10.25,23,17.25,12.75,23,17.25,12.75,23,15.5,10.25,23,15.5,3,30,0,2,30,0,2,16,0,3,16,0,2,30,1,3,30,1,3,16,1,2,16,1,2,30,0,2,30,1,2,16,1,2,16,0,3,30,1,3,30,0,3,16,0,3,16,1,2,16,1,3,16,1,3,16,0,2,16,0,14,30,0,13,30,0,13,16,0,14,16,0,13,30,1,14,30,1,14,16,1,13,16,1,13,30,0,13,30,1,13,16,1,13,16,0,14,30,1,14,30,0,14,16,0,14,16,1,13,16,1,14,16,1,14,16,0,13,16,0,13,30,0,3,30,0,3,29,0,13,29,0,3,30,1,13,30,1,13,29,1,3,29,1,3,30,0,3,30,1,3,29,1,3,29,0,13,30,1,13,30,0,13,29,0,13,29,1,3,29,1,13,29,1,13,29,0,3,29,0,13,20.5,1,3,20.5,1,3,16.5,1,13,16.5,1,3,20.5,1,3,20.5,3,3,16.5,3,3,16.5,1,13,20.5,3,13,20.5,1,13,16.5,1,13,16.5,3,3,20.5,1,13,20.5,1,13,20.5,3,3,20.5,3,3,16.5,3,13,16.5,3,13,16.5,1,3,16.5,1,13,25,2,3,25,2,3,21,2,13,21,2,3,25,2,3,25,3,3,21,3,3,21,2,13,25,3,13,25,2,13,21,2,13,21,3,3,25,2,13,25,2,13,25,3,3,25,3,3,21,3,13,21,3,13,21,2,3,21,2,13,29.5,1.5,3,29.5,1.5,3,25.5,1.5,13,25.5,1.5,3,29.5,1.5,3,29.5,3,3,25.5,3,3,25.5,1.5,13,29.5,3,13,29.5,1.5,13,25.5,1.5,13,25.5,3,3,29.5,1.5,13,29.5,1.5,13,29.5,3,3,29.5,3,3,25.5,3,13,25.5,3,13,25.5,1.5,3,25.5,1.5,12,20,-0.75,11,20,-0.75,11,16,-0.75,12,16,-0.75,11,20,-0.25,12,20,-0.25,12,16,-0.25,11,16,-0.25,11,20,-0.75,11,20,-0.25,11,16,-0.25,11,16,-0.75,12,20,-0.25,12,20,-0.75,12,16,-0.75,12,16,-0.25,11,20,-0.75,12,20,-0.75,12,20,-0.25,11,20,-0.25,11,16,-0.25,12,16,-0.25,12,16,-0.75,11,16,-0.75,11.25,19.5,-0.25,11.25,19.5,0.5,11.25,19,0.5,11.25,19,-0.25,11.75,19.5,0.5,11.75,19.5,-0.25,11.75,19,-0.25,11.75,19,0.5,11.25,19.5,-0.25,11.75,19.5,-0.25,11.75,19.5,0.5,11.25,19.5,0.5,11.25,19,0.5,11.75,19,0.5,11.75,19,-0.25,11.25,19,-0.25,12,32.5,4,4,32.5,4,4,32,4,12,32,4,4,32.5,12,12,32.5,12,12,32,12,4,32,12,4,32.5,4,4,32.5,12,4,32,12,4,32,4,12,32.5,12,12,32.5,4,12,32,4,12,32,12,4,32.5,4,12,32.5,4,12,32.5,12,4,32.5,12,11,33.5,3,5,33.5,3,5,32,3,11,32,3,5,33.5,4,11,33.5,4,11,32,4,5,32,4,5,33.5,3,5,33.5,4,5,32,4,5,32,3,11,33.5,4,11,33.5,3,11,32,3,11,32,4,5,33.5,3,11,33.5,3,11,33.5,4,5,33.5,4,11,33.5,12,5,33.5,12,5,32,12,11,32,12,5,33.5,13,11,33.5,13,11,32,13,5,32,13,5,33.5,12,5,33.5,13,5,32,13,5,32,12,11,33.5,13,11,33.5,12,11,32,12,11,32,13,5,33.5,12,11,33.5,12,11,33.5,13,5,33.5,13,4,33.5,5,3,33.5,5,3,32,5,4,32,5,3,33.5,11,4,33.5,11,4,32,11,3,32,11,3,33.5,5,3,33.5,11,3,32,11,3,32,5,4,33.5,11,4,33.5,5,4,32,5,4,32,11,3,33.5,5,4,33.5,5,4,33.5,11,3,33.5,11,13,33.5,5,12,33.5,5,12,32,5,13,32,5,12,33.5,11,13,33.5,11,13,32,11,12,32,11,12,33.5,5,12,33.5,11,12,32,11,12,32,5,13,33.5,11,13,33.5,5,13,32,5,13,32,11,12,33.5,5,13,33.5,5,13,33.5,11,12,33.5,11,4.96,33.4,2.84,2.84,33.4,4.96,2.84,32,4.96,4.96,32,2.84,3.54,33.4,5.66,5.66,33.4,3.54,5.66,32,3.54,3.54,32,5.66,2.84,33.4,4.96,3.54,33.4,5.66,3.54,32,5.66,2.84,32,4.96,5.66,33.4,3.54,4.96,33.4,2.84,4.96,32,2.84,5.66,32,3.54,2.84,33.4,4.96,4.96,33.4,2.84,5.66,33.4,3.54,3.54,33.4,5.66,13.16,33.4,4.96,11.04,33.4,2.84,11.04,32,2.84,13.16,32,4.96,10.34,33.4,3.54,12.46,33.4,5.66,12.46,32,5.66,10.34,32,3.54,11.04,33.4,2.84,10.34,33.4,3.54,10.34,32,3.54,11.04,32,2.84,12.46,33.4,5.66,13.16,33.4,4.96,13.16,32,4.96,12.46,32,5.66,11.04,33.4,2.84,13.16,33.4,4.96,12.46,33.4,5.66,10.34,33.4,3.54,5.66,33.4,12.46,3.54,33.4,10.34,3.54,32,10.34,5.66,32,12.46,2.84,33.4,11.04,4.96,33.4,13.16,4.96,32,13.16,2.84,32,11.04,3.54,33.4,10.34,2.84,33.4,11.04,2.84,32,11.04,3.54,32,10.34,4.96,33.4,13.16,5.66,33.4,12.46,5.66,32,12.46,4.96,32,13.16,3.54,33.4,10.34,5.66,33.4,12.46,4.96,33.4,13.16,2.84,33.4,11.04,12.46,33.4,10.34,10.34,33.4,12.46,10.34,32,12.46,12.46,32,10.34,11.04,33.4,13.16,13.16,33.4,11.04,13.16,32,11.04,11.04,32,13.16,10.34,33.4,12.46,11.04,33.4,13.16,11.04,32,13.16,10.34,32,12.46,13.16,33.4,11.04,12.46,33.4,10.34,12.46,32,10.34,13.16,32,11.04,10.34,33.4,12.46,12.46,33.4,10.34,13.16,33.4,11.04,11.04,33.4,13.16,1.5,32.5,0.5,0.5,32.5,0.5,0.5,32,0.5,1.5,32,0.5,0.5,32.5,1.5,1.5,32.5,1.5,1.5,32,1.5,0.5,32,1.5,0.5,32.5,0.5,0.5,32.5,1.5,0.5,32,1.5,0.5,32,0.5,1.5,32.5,1.5,1.5,32.5,0.5,1.5,32,0.5,1.5,32,1.5,0.5,32.5,0.5,1.5,32.5,0.5,1.5,32.5,1.5,0.5,32.5,1.5,1.25,34,0.75,0.75,34,0.75,0.75,32.5,0.75,1.25,32.5,0.75,0.75,34,1.25,1.25,34,1.25,1.25,32.5,1.25,0.75,32.5,1.25,0.75,34,0.75,0.75,34,1.25,0.75,32.5,1.25,0.75,32.5,0.75,1.25,34,1.25,1.25,34,0.75,1.25,32.5,0.75,1.25,32.5,1.25,15.5,32.5,0.5,14.5,32.5,0.5,14.5,32,0.5,15.5,32,0.5,14.5,32.5,1.5,15.5,32.5,1.5,15.5,32,1.5,14.5,32,1.5,14.5,32.5,0.5,14.5,32.5,1.5,14.5,32,1.5,14.5,32,0.5,15.5,32.5,1.5,15.5,32.5,0.5,15.5,32,0.5,15.5,32,1.5,14.5,32.5,0.5,15.5,32.5,0.5,15.5,32.5,1.5,14.5,32.5,1.5,15.25,35.2,0.75,14.75,35.2,0.75,14.75,32.5,0.75,15.25,32.5,0.75,14.75,35.2,1.25,15.25,35.2,1.25,15.25,32.5,1.25,14.75,32.5,1.25,14.75,35.2,0.75,14.75,35.2,1.25,14.75,32.5,1.25,14.75,32.5,0.75,15.25,35.2,1.25,15.25,35.2,0.75,15.25,32.5,0.75,15.25,32.5,1.25,1.5,32.5,14.5,0.5,32.5,14.5,0.5,32,14.5,1.5,32,14.5,0.5,32.5,15.5,1.5,32.5,15.5,1.5,32,15.5,0.5,32,15.5,0.5,32.5,14.5,0.5,32.5,15.5,0.5,32,15.5,0.5,32,14.5,1.5,32.5,15.5,1.5,32.5,14.5,1.5,32,14.5,1.5,32,15.5,0.5,32.5,14.5,1.5,32.5,14.5,1.5,32.5,15.5,0.5,32.5,15.5,1.25,34.6,14.75,0.75,34.6,14.75,0.75,32.5,14.75,1.25,32.5,14.75,0.75,34.6,15.25,1.25,34.6,15.25,1.25,32.5,15.25,0.75,32.5,15.25,0.75,34.6,14.75,0.75,34.6,15.25,0.75,32.5,15.25,0.75,32.5,14.75,1.25,34.6,15.25,1.25,34.6,14.75,1.25,32.5,14.75,1.25,32.5,15.25,15.5,32.5,14.5,14.5,32.5,14.5,14.5,32,14.5,15.5,32,14.5,14.5,32.5,15.5,15.5,32.5,15.5,15.5,32,15.5,14.5,32,15.5,14.5,32.5,14.5,14.5,32.5,15.5,14.5,32,15.5,14.5,32,14.5,15.5,32.5,15.5,15.5,32.5,14.5,15.5,32,14.5,15.5,32,15.5,14.5,32.5,14.5,15.5,32.5,14.5,15.5,32.5,15.5,14.5,32.5,15.5,15.25,33.4,14.75,14.75,33.4,14.75,14.75,32.5,14.75,15.25,32.5,14.75,14.75,33.4,15.25,15.25,33.4,15.25,15.25,32.5,15.25,14.75,32.5,15.25,14.75,33.4,14.75,14.75,33.4,15.25,14.75,32.5,15.25,14.75,32.5,14.75,15.25,33.4,15.25,15.25,33.4,14.75,15.25,32.5,14.75,15.25,32.5,15.25],"uv":[0.5,0,0.625,0,0.625,0.0156,0.5,0.0156,0.75,0,0.875,0,0.875,0.0156,0.75,0.0156,0.625,0,0.75,0,0.75,0.0156,0.625,0.0156,0.625,0,0.75,0,0.75,0.0156,0.625,0.0156,0.875,0,1,0,1,0.125,0.875,0.125,0.125,0.125,0.25,0.125,0.25,0.25,0.125,0.25,0.5234,0.1406,0.6016,0.1406,0.6016,0.25,0.5234,0.25,0.5234,0.1406,0.6094,0.1406,0.6094,0.25,0.5234,0.25,0.5156,0.1406,0.6016,0.1406,0.6016,0.25,0.5156,0.25,0.6094,0.0156,0.625,0.0156,0.625,0.125,0.6094,0.125,0.75,0.0156,0.7656,0.0156,0.7656,0.125,0.75,0.125,0.625,0.0156,0.6406,0.0156,0.6406,0.125,0.625,0.125,0.7344,0.0156,0.75,0.0156,0.75,0.125,0.7344,0.125,0.125,0.2344,0.1406,0.2344,0.1406,0.25,0.125,0.25,0.5,0.0156,0.5156,0.0156,0.5156,0.125,0.5,0.125,0.8594,0.0156,0.875,0.0156,0.875,0.125,0.8594,0.125,0.625,0.0156,0.6406,0.0156,0.6406,0.125,0.625,0.125,0.7344,0.0156,0.75,0.0156,0.75,0.125,0.7344,0.125,0.2344,0.2344,0.25,0.2344,0.25,0.25,0.2344,0.25,0.6094,0.0156,0.625,0.0156,0.625,0.125,0.6094,0.125,0.75,0.0156,0.7656,0.0156,0.7656,0.125,0.75,0.125,0.7344,0.0156,0.75,0.0156,0.75,0.125,0.7344,0.125,0.625,0.0156,0.6406,0.0156,0.6406,0.125,0.625,0.125,0.125,0.125,0.1406,0.125,0.1406,0.1406,0.125,0.1406,0.5,0.0156,0.5156,0.0156,0.5156,0.125,0.5,0.125,0.8594,0.0156,0.875,0.0156,0.875,0.125,0.8594,0.125,0.7344,0.0156,0.75,0.0156,0.75,0.125,0.7344,0.125,0.625,0.0156,0.6406,0.0156,0.6406,0.125,0.625,0.125,0.2344,0.125,0.25,0.125,0.25,0.1406,0.2344,0.1406,0.6016,0.1016,0.6172,0.1016,0.6172,0.125,0.6016,0.125,0.7578,0.1016,0.7734,0.1016,0.7734,0.125,0.7578,0.125,0.6406,0.1016,0.7344,0.1016,0.7344,0.125,0.6406,0.125,0.5156,0.2266,0.6094,0.2266,0.6094,0.25,0.5156,0.25,0.8828,0.0156,0.8984,0.0156,0.8984,0.1094,0.8828,0.1094,0.1328,0.1406,0.1484,0.1406,0.1484,0.2344,0.1328,0.2344,0.6016,0.0156,0.6172,0.0156,0.6172,0.0391,0.6016,0.0391,0.7578,0.0156,0.7734,0.0156,0.7734,0.0391,0.7578,0.0391,0.6406,0.0156,0.7344,0.0156,0.7344,0.0391,0.6406,0.0391,0.5156,0.1406,0.6094,0.1406,0.6094,0.1641,0.5156,0.1641,0.8828,0.0156,0.8984,0.0156,0.8984,0.1094,0.8828,0.1094,0.1328,0.1406,0.1484,0.1406,0.1484,0.2344,0.1328,0.2344,0.6016,0.0391,0.6172,0.0391,0.6172,0.1016,0.6016,0.1016,0.7578,0.0391,0.7734,0.0391,0.7734,0.1016,0.7578,0.1016,0.6406,0.0391,0.6563,0.0391,0.6563,0.1016,0.6406,0.1016,0.5938,0.1641,0.6094,0.1641,0.6094,0.2266,0.5938,0.2266,0.8828,0.0156,0.8984,0.0156,0.8984,0.0313,0.8828,0.0313,0.1328,0.2188,0.1484,0.2188,0.1484,0.2344,0.1328,0.2344,0.6016,0.0391,0.6172,0.0391,0.6172,0.1016,0.6016,0.1016,0.7578,0.0391,0.7734,0.0391,0.7734,0.1016,0.7578,0.1016,0.7188,0.0391,0.7344,0.0391,0.7344,0.1016,0.7188,0.1016,0.5156,0.1641,0.5313,0.1641,0.5313,0.2266,0.5156,0.2266,0.8828,0.0938,0.8984,0.0938,0.8984,0.1094,0.8828,0.1094,0.1328,0.1406,0.1484,0.1406,0.1484,0.1563,0.1328,0.1563,0.2813,0.2148,0.3438,0.2148,0.3438,0.2227,0.2813,0.2227,0.1563,0.2148,0.2188,0.2148,0.2188,0.2227,0.1563,0.2227,0.1367,0.1563,0.1445,0.1563,0.1445,0.2188,0.1367,0.2188,0.1367,0.1563,0.1445,0.1563,0.1445,0.2188,0.1367,0.2188,0.2813,0.1992,0.3438,0.1992,0.3438,0.207,0.2813,0.207,0.1563,0.1992,0.2188,0.1992,0.2188,0.207,0.1563,0.207,0.1367,0.1563,0.1445,0.1563,0.1445,0.2188,0.1367,0.2188,0.1367,0.1563,0.1445,0.1563,0.1445,0.2188,0.1367,0.2188,0.2813,0.1836,0.3438,0.1836,0.3438,0.1914,0.2813,0.1914,0.1563,0.1836,0.2188,0.1836,0.2188,0.1914,0.1563,0.1914,0.1367,0.1563,0.1445,0.1563,0.1445,0.2188,0.1367,0.2188,0.1367,0.1563,0.1445,0.1563,0.1445,0.2188,0.1367,0.2188,0.2813,0.168,0.3438,0.168,0.3438,0.1758,0.2813,0.1758,0.1563,0.168,0.2188,0.168,0.2188,0.1758,0.1563,0.1758,0.1367,0.1563,0.1445,0.1563,0.1445,0.2188,0.1367,0.2188,0.1367,0.1563,0.1445,0.1563,0.1445,0.2188,0.1367,0.2188,0.5078,0.1016,0.5234,0.1016,0.5234,0.125,0.5078,0.125,0.8516,0.1016,0.8672,0.1016,0.8672,0.125,0.8516,0.125,0.5156,0.2266,0.6094,0.2266,0.6094,0.25,0.5156,0.25,0.6406,0.1016,0.7344,0.1016,0.7344,0.125,0.6406,0.125,0.9766,0.0156,0.9922,0.0156,0.9922,0.1094,0.9766,0.1094,0.2266,0.1406,0.2422,0.1406,0.2422,0.2344,0.2266,0.2344,0.5078,0.0156,0.5234,0.0156,0.5234,0.0391,0.5078,0.0391,0.8516,0.0156,0.8672,0.0156,0.8672,0.0391,0.8516,0.0391,0.5156,0.1406,0.6094,0.1406,0.6094,0.1641,0.5156,0.1641,0.6406,0.0156,0.7344,0.0156,0.7344,0.0391,0.6406,0.0391,0.9766,0.0156,0.9922,0.0156,0.9922,0.1094,0.9766,0.1094,0.2266,0.1406,0.2422,0.1406,0.2422,0.2344,0.2266,0.2344,0.5078,0.0391,0.5234,0.0391,0.5234,0.1016,0.5078,0.1016,0.8516,0.0391,0.8672,0.0391,0.8672,0.1016,0.8516,0.1016,0.5156,0.1641,0.5313,0.1641,0.5313,0.2266,0.5156,0.2266,0.7188,0.0391,0.7344,0.0391,0.7344,0.1016,0.7188,0.1016,0.9766,0.0156,0.9922,0.0156,0.9922,0.0313,0.9766,0.0313,0.2266,0.2188,0.2422,0.2188,0.2422,0.2344,0.2266,0.2344,0.5078,0.0391,0.5234,0.0391,0.5234,0.1016,0.5078,0.1016,0.8516,0.0391,0.8672,0.0391,0.8672,0.1016,0.8516,0.1016,0.5938,0.1641,0.6094,0.1641,0.6094,0.2266,0.5938,0.2266,0.6406,0.0391,0.6563,0.0391,0.6563,0.1016,0.6406,0.1016,0.9766,0.0938,0.9922,0.0938,0.9922,0.1094,0.9766,0.1094,0.2266,0.1406,0.2422,0.1406,0.2422,0.1563,0.2266,0.1563,0.1563,0.2148,0.2188,0.2148,0.2188,0.2227,0.1563,0.2227,0.2813,0.2148,0.3438,0.2148,0.3438,0.2227,0.2813,0.2227,0.2305,0.1563,0.2383,0.1563,0.2383,0.2188,0.2305,0.2188,0.2305,0.1563,0.2383,0.1563,0.2383,0.2188,0.2305,0.2188,0.1563,0.1992,0.2188,0.1992,0.2188,0.207,0.1563,0.207,0.2813,0.1992,0.3438,0.1992,0.3438,0.207,0.2813,0.207,0.2305,0.1563,0.2383,0.1563,0.2383,0.2188,0.2305,0.2188,0.2305,0.1563,0.2383,0.1563,0.2383,0.2188,0.2305,0.2188,0.1563,0.1836,0.2188,0.1836,0.2188,0.1914,0.1563,0.1914,0.2813,0.1836,0.3438,0.1836,0.3438,0.1914,0.2813,0.1914,0.2305,0.1563,0.2383,0.1563,0.2383,0.2188,0.2305,0.2188,0.2305,0.1563,0.2383,0.1563,0.2383,0.2188,0.2305,0.2188,0.1563,0.168,0.2188,0.168,0.2188,0.1758,0.1563,0.1758,0.2813,0.168,0.3438,0.168,0.3438,0.1758,0.2813,0.1758,0.2305,0.1563,0.2383,0.1563,0.2383,0.2188,0.2305,0.2188,0.2305,0.1563,0.2383,0.1563,0.2383,0.2188,0.2305,0.2188,0.7656,0.0156,0.8594,0.0156,0.8594,0.125,0.7656,0.125,0.25,0.25,0.3281,0.25,0.3281,0.3047,0.25,0.3047,0.4922,0.1563,0.4961,0.1563,0.4961,0.2109,0.4922,0.2109,0.3789,0.1563,0.3828,0.1563,0.3828,0.2109,0.3789,0.2109,0.3984,0.2422,0.4766,0.2422,0.4766,0.2461,0.3984,0.2461,0.3984,0.1289,0.4766,0.1289,0.4766,0.1328,0.3984,0.1328,0.9023,0.1953,0.918,0.1953,0.918,0.25,0.9023,0.25,0.875,0.1953,0.8828,0.1953,0.8828,0.25,0.875,0.25,0.875,0.1953,0.8828,0.1953,0.8828,0.25,0.875,0.25,0.25,0.3125,0.2734,0.3125,0.2734,0.3359,0.25,0.3359,0.375,0.1758,0.3848,0.1758,0.3848,0.1953,0.375,0.1953,0.375,0.1758,0.3789,0.1758,0.3789,0.1953,0.375,0.1953,0.4004,0.125,0.4199,0.125,0.4199,0.1348,0.4004,0.1348,0.4004,0.125,0.4199,0.125,0.4199,0.1289,0.4004,0.1289,0.9297,0.1953,0.9453,0.1953,0.9453,0.25,0.9297,0.25,0.875,0.1953,0.8828,0.1953,0.8828,0.25,0.875,0.25,0.875,0.1953,0.8828,0.1953,0.8828,0.25,0.875,0.25,0.25,0.3125,0.2734,0.3125,0.2734,0.3359,0.25,0.3359,0.375,0.1758,0.3848,0.1758,0.3848,0.1953,0.375,0.1953,0.375,0.1758,0.3789,0.1758,0.3789,0.1953,0.375,0.1953,0.4277,0.125,0.4473,0.125,0.4473,0.1348,0.4277,0.1348,0.4277,0.125,0.4473,0.125,0.4473,0.1289,0.4277,0.1289,0.957,0.1953,0.9727,0.1953,0.9727,0.25,0.957,0.25,0.875,0.1953,0.8828,0.1953,0.8828,0.25,0.875,0.25,0.875,0.1953,0.8828,0.1953,0.8828,0.25,0.875,0.25,0.25,0.3125,0.2734,0.3125,0.2734,0.3359,0.25,0.3359,0.375,0.1758,0.3848,0.1758,0.3848,0.1953,0.375,0.1953,0.375,0.1758,0.3789,0.1758,0.3789,0.1953,0.375,0.1953,0.4551,0.125,0.4746,0.125,0.4746,0.1348,0.4551,0.1348,0.4551,0.125,0.4746,0.125,0.4746,0.1289,0.4551,0.1289,0.4766,0.1406,0.4844,0.1406,0.4844,0.25,0.4766,0.25,0.3906,0.1406,0.3984,0.1406,0.3984,0.25,0.3906,0.25,0.375,0.1406,0.3828,0.1406,0.3828,0.25,0.375,0.25,0.4922,0.1406,0.5,0.1406,0.5,0.25,0.4922,0.25,0.3906,0.2422,0.3984,0.2422,0.3984,0.25,0.3906,0.25,0.3906,0.1406,0.3984,0.1406,0.3984,0.25,0.3906,0.25,0.4766,0.1406,0.4844,0.1406,0.4844,0.25,0.4766,0.25,0.375,0.1406,0.3828,0.1406,0.3828,0.25,0.375,0.25,0.4922,0.1406,0.5,0.1406,0.5,0.25,0.4922,0.25,0.4766,0.2422,0.4844,0.2422,0.4844,0.25,0.4766,0.25,0.3984,0.1406,0.4766,0.1406,0.4766,0.1484,0.3984,0.1484,0.3984,0.1406,0.4766,0.1406,0.4766,0.1484,0.3984,0.1484,0.375,0.1406,0.3828,0.1406,0.3828,0.1484,0.375,0.1484,0.4922,0.1406,0.5,0.1406,0.5,0.1484,0.4922,0.1484,0.3984,0.2422,0.4766,0.2422,0.4766,0.25,0.3984,0.25,0.125,0.25,0.2031,0.25,0.2031,0.2813,0.125,0.2813,0.3828,0.2148,0.3984,0.2148,0.3984,0.2461,0.3828,0.2461,0.4766,0.2148,0.4922,0.2148,0.4922,0.2461,0.4766,0.2461,0.3984,0.1328,0.4766,0.1328,0.4766,0.1484,0.3984,0.1484,0.3984,0.2266,0.4766,0.2266,0.4766,0.2422,0.3984,0.2422,0.125,0.2813,0.2031,0.2813,0.2031,0.3125,0.125,0.3125,0.3906,0.1797,0.3984,0.1797,0.3984,0.2109,0.3906,0.2109,0.4766,0.1797,0.4844,0.1797,0.4844,0.2109,0.4766,0.2109,0.3984,0.1406,0.4766,0.1406,0.4766,0.1484,0.3984,0.1484,0.3984,0.2266,0.4766,0.2266,0.4766,0.2344,0.3984,0.2344,0.125,0.3125,0.2031,0.3125,0.2031,0.3438,0.125,0.3438,0.3867,0.1445,0.3984,0.1445,0.3984,0.1758,0.3867,0.1758,0.4766,0.1445,0.4883,0.1445,0.4883,0.1758,0.4766,0.1758,0.3984,0.1367,0.4766,0.1367,0.4766,0.1484,0.3984,0.1484,0.3984,0.2266,0.4766,0.2266,0.4766,0.2383,0.3984,0.2383,0.2813,0.2188,0.2891,0.2188,0.2891,0.25,0.2813,0.25,0.3359,0.2188,0.3438,0.2188,0.3438,0.25,0.3359,0.25,0.3691,0.2188,0.3731,0.2188,0.3731,0.25,0.3691,0.25,0.252,0.2188,0.2559,0.2188,0.2559,0.25,0.252,0.25,0.3359,0.2441,0.3438,0.2441,0.3438,0.2481,0.3359,0.2481,0.3359,0.127,0.3438,0.127,0.3438,0.1309,0.3359,0.1309,0.125,0.2227,0.1289,0.2227,0.1289,0.2266,0.125,0.2266,0.125,0.2227,0.127,0.2227,0.127,0.2266,0.125,0.2266,0.2129,0.125,0.2168,0.125,0.2168,0.1289,0.2129,0.1289,0.2129,0.125,0.2168,0.125,0.2168,0.127,0.2129,0.127,0.5313,0.2461,0.5938,0.2461,0.5938,0.25,0.5313,0.25,0.5313,0.2461,0.5938,0.2461,0.5938,0.25,0.5313,0.25,0.5313,0.2461,0.5938,0.2461,0.5938,0.25,0.5313,0.25,0.5313,0.2461,0.5938,0.2461,0.5938,0.25,0.5313,0.25,0.5313,0.1563,0.5938,0.1563,0.5938,0.2188,0.5313,0.2188,0.1641,0.2383,0.2109,0.2383,0.2109,0.25,0.1641,0.25,0.1641,0.2383,0.2109,0.2383,0.2109,0.25,0.1641,0.25,0.1484,0.2383,0.1563,0.2383,0.1563,0.25,0.1484,0.25,0.2188,0.2383,0.2266,0.2383,0.2266,0.25,0.2188,0.25,0.2891,0.1484,0.3359,0.1484,0.3359,0.1563,0.2891,0.1563,0.1641,0.2383,0.2109,0.2383,0.2109,0.25,0.1641,0.25,0.1641,0.2383,0.2109,0.2383,0.2109,0.25,0.1641,0.25,0.2188,0.2383,0.2266,0.2383,0.2266,0.25,0.2188,0.25,0.1484,0.2383,0.1563,0.2383,0.1563,0.25,0.1484,0.25,0.2891,0.2188,0.3359,0.2188,0.3359,0.2266,0.2891,0.2266,0.2188,0.2383,0.2266,0.2383,0.2266,0.25,0.2188,0.25,0.1484,0.2383,0.1563,0.2383,0.1563,0.25,0.1484,0.25,0.1641,0.2383,0.2109,0.2383,0.2109,0.25,0.1641,0.25,0.1641,0.2383,0.2109,0.2383,0.2109,0.25,0.1641,0.25,0.2734,0.1641,0.2813,0.1641,0.2813,0.2109,0.2734,0.2109,0.1484,0.2383,0.1563,0.2383,0.1563,0.25,0.1484,0.25,0.2188,0.2383,0.2266,0.2383,0.2266,0.25,0.2188,0.25,0.1641,0.2383,0.2109,0.2383,0.2109,0.25,0.1641,0.25,0.1641,0.2383,0.2109,0.2383,0.2109,0.25,0.1641,0.25,0.3438,0.1641,0.3516,0.1641,0.3516,0.2109,0.3438,0.2109,0.2051,0.2391,0.2285,0.2391,0.2285,0.25,0.2051,0.25,0.1465,0.2391,0.1699,0.2391,0.1699,0.25,0.1465,0.25,0.1543,0.2391,0.1621,0.2391,0.1621,0.25,0.1543,0.25,0.2129,0.2391,0.2207,0.2391,0.2207,0.25,0.2129,0.25,0.2715,0.1543,0.2949,0.1543,0.2949,0.1621,0.2715,0.1621,0.1465,0.2391,0.1699,0.2391,0.1699,0.25,0.1465,0.25,0.2051,0.2391,0.2285,0.2391,0.2285,0.25,0.2051,0.25,0.1543,0.2391,0.1621,0.2391,0.1621,0.25,0.1543,0.25,0.2129,0.2391,0.2207,0.2391,0.2207,0.25,0.2129,0.25,0.3301,0.1543,0.3535,0.1543,0.3535,0.1621,0.3301,0.1621,0.2051,0.2391,0.2285,0.2391,0.2285,0.25,0.2051,0.25,0.1465,0.2391,0.1699,0.2391,0.1699,0.25,0.1465,0.25,0.2129,0.2391,0.2207,0.2391,0.2207,0.25,0.2129,0.25,0.1543,0.2391,0.1621,0.2391,0.1621,0.25,0.1543,0.25,0.2715,0.2129,0.2949,0.2129,0.2949,0.2207,0.2715,0.2207,0.1465,0.2391,0.1699,0.2391,0.1699,0.25,0.1465,0.25,0.2051,0.2391,0.2285,0.2391,0.2285,0.25,0.2051,0.25,0.2129,0.2391,0.2207,0.2391,0.2207,0.25,0.2129,0.25,0.1543,0.2391,0.1621,0.2391,0.1621,0.25,0.1543,0.25,0.3301,0.2129,0.3535,0.2129,0.3535,0.2207,0.3301,0.2207,0.4883,0.2461,0.4961,0.2461,0.4961,0.25,0.4883,0.25,0.3789,0.2461,0.3867,0.2461,0.3867,0.25,0.3789,0.25,0.3789,0.2461,0.3867,0.2461,0.3867,0.25,0.3789,0.25,0.4883,0.2461,0.4961,0.2461,0.4961,0.25,0.4883,0.25,0.3789,0.1289,0.3867,0.1289,0.3867,0.1367,0.3789,0.1367,0.4902,0.2344,0.4941,0.2344,0.4941,0.2461,0.4902,0.2461,0.3809,0.2344,0.3848,0.2344,0.3848,0.2461,0.3809,0.2461,0.3809,0.2344,0.3848,0.2344,0.3848,0.2461,0.3809,0.2461,0.4902,0.2344,0.4941,0.2344,0.4941,0.2461,0.4902,0.2461,0.3789,0.2461,0.3867,0.2461,0.3867,0.25,0.3789,0.25,0.4883,0.2461,0.4961,0.2461,0.4961,0.25,0.4883,0.25,0.3789,0.2461,0.3867,0.2461,0.3867,0.25,0.3789,0.25,0.4883,0.2461,0.4961,0.2461,0.4961,0.25,0.4883,0.25,0.4883,0.1289,0.4961,0.1289,0.4961,0.1367,0.4883,0.1367,0.3809,0.225,0.3848,0.225,0.3848,0.2461,0.3809,0.2461,0.4902,0.225,0.4941,0.225,0.4941,0.2461,0.4902,0.2461,0.3809,0.225,0.3848,0.225,0.3848,0.2461,0.3809,0.2461,0.4902,0.225,0.4941,0.225,0.4941,0.2461,0.4902,0.2461,0.4883,0.2461,0.4961,0.2461,0.4961,0.25,0.4883,0.25,0.3789,0.2461,0.3867,0.2461,0.3867,0.25,0.3789,0.25,0.4883,0.2461,0.4961,0.2461,0.4961,0.25,0.4883,0.25,0.3789,0.2461,0.3867,0.2461,0.3867,0.25,0.3789,0.25,0.3789,0.2383,0.3867,0.2383,0.3867,0.2461,0.3789,0.2461,0.4902,0.2297,0.4941,0.2297,0.4941,0.2461,0.4902,0.2461,0.3809,0.2297,0.3848,0.2297,0.3848,0.2461,0.3809,0.2461,0.4902,0.2297,0.4941,0.2297,0.4941,0.2461,0.4902,0.2461,0.3809,0.2297,0.3848,0.2297,0.3848,0.2461,0.3809,0.2461,0.3789,0.2461,0.3867,0.2461,0.3867,0.25,0.3789,0.25,0.4883,0.2461,0.4961,0.2461,0.4961,0.25,0.4883,0.25,0.4883,0.2461,0.4961,0.2461,0.4961,0.25,0.4883,0.25,0.3789,0.2461,0.3867,0.2461,0.3867,0.25,0.3789,0.25,0.4883,0.2383,0.4961,0.2383,0.4961,0.2461,0.4883,0.2461,0.3809,0.2391,0.3848,0.2391,0.3848,0.2461,0.3809,0.2461,0.4902,0.2391,0.4941,0.2391,0.4941,0.2461,0.4902,0.2461,0.4902,0.2391,0.4941,0.2391,0.4941,0.2461,0.4902,0.2461,0.3809,0.2391,0.3848,0.2391,0.3848,0.2461,0.3809,0.2461],"shade":[0.8,0.8,0.6,0.6,1,0.5,0.8,0.6,0.6,0.8,0.8,0.6,0.6,0.5,0.8,0.8,0.6,0.6,0.5,0.8,0.8,0.6,0.6,0.5,0.8,0.8,0.6,0.6,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.66,0.59,0.94,0.51,0.66,0.59,0.94,0.51,0.66,0.59,0.94,0.51,0.66,0.59,0.94,0.51,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.59,0.66,0.94,0.51,0.59,0.66,0.94,0.51,0.59,0.66,0.94,0.51,0.59,0.66,0.94,0.51,0.8,0.8,0.6,0.6,1,0.5,0.8,0.6,0.6,0.8,0.6,0.6,1,0.5,0.8,0.6,0.6,0.8,0.6,0.6,1,0.5,0.8,0.6,0.6,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,0.5,0.8,0.8,0.6,0.6,0.5,0.8,0.8,0.6,0.6,0.5,0.8,0.6,0.6,1,0.5,0.8,0.6,0.6,1,0.5,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.8,0.8,0.6,0.6,1,0.8,0.8,0.6,0.6,1,0.8,0.8,0.6,0.6,1,0.8,0.8,0.6,0.6,1,0.7,0.7,0.7,0.7,1,0.7,0.7,0.7,0.7,1,0.7,0.7,0.7,0.7,1,0.7,0.7,0.7,0.7,1,0.8,0.8,0.6,0.6,1,0.8,0.8,0.6,0.6,0.8,0.8,0.6,0.6,1,0.8,0.8,0.6,0.6,0.8,0.8,0.6,0.6,1,0.8,0.8,0.6,0.6,0.8,0.8,0.6,0.6,1,0.8,0.8,0.6,0.6],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22,24,26,25,24,27,26,28,30,29,28,31,30,32,34,33,32,35,34,36,38,37,36,39,38,40,42,41,40,43,42,44,46,45,44,47,46,48,50,49,48,51,50,52,54,53,52,55,54,56,58,57,56,59,58,60,62,61,60,63,62,64,66,65,64,67,66,68,70,69,68,71,70,72,74,73,72,75,74,76,78,77,76,79,78,80,82,81,80,83,82,84,86,85,84,87,86,88,90,89,88,91,90,92,94,93,92,95,94,96,98,97,96,99,98,100,102,101,100,103,102,104,106,105,104,107,106,108,110,109,108,111,110,112,114,113,112,115,114,116,118,117,116,119,118,120,122,121,120,123,122,124,126,125,124,127,126,128,130,129,128,131,130,132,134,133,132,135,134,136,138,137,136,139,138,140,142,141,140,143,142,144,146,145,144,147,146,148,150,149,148,151,150,152,154,153,152,155,154,156,158,157,156,159,158,160,162,161,160,163,162,164,166,165,164,167,166,168,170,169,168,171,170,172,174,173,172,175,174,176,178,177,176,179,178,180,182,181,180,183,182,184,186,185,184,187,186,188,190,189,188,191,190,192,194,193,192,195,194,196,198,197,196,199,198,200,202,201,200,203,202,204,206,205,204,207,206,208,210,209,208,211,210,212,214,213,212,215,214,216,218,217,216,219,218,220,222,221,220,223,222,224,226,225,224,227,226,228,230,229,228,231,230,232,234,233,232,235,234,236,238,237,236,239,238,240,242,241,240,243,242,244,246,245,244,247,246,248,250,249,248,251,250,252,254,253,252,255,254,256,258,257,256,259,258,260,262,261,260,263,262,264,266,265,264,267,266,268,270,269,268,271,270,272,274,273,272,275,274,276,278,277,276,279,278,280,282,281,280,283,282,284,286,285,284,287,286,288,290,289,288,291,290,292,294,293,292,295,294,296,298,297,296,299,298,300,302,301,300,303,302,304,306,305,304,307,306,308,310,309,308,311,310,312,314,313,312,315,314,316,318,317,316,319,318,320,322,321,320,323,322,324,326,325,324,327,326,328,330,329,328,331,330,332,334,333,332,335,334,336,338,337,336,339,338,340,342,341,340,343,342,344,346,345,344,347,346,348,350,349,348,351,350,352,354,353,352,355,354,356,358,357,356,359,358,360,362,361,360,363,362,364,366,365,364,367,366,368,370,369,368,371,370,372,374,373,372,375,374,376,378,377,376,379,378,380,382,381,380,383,382,384,386,385,384,387,386,388,390,389,388,391,390,392,394,393,392,395,394,396,398,397,396,399,398,400,402,401,400,403,402,404,406,405,404,407,406,408,410,409,408,411,410,412,414,413,412,415,414,416,418,417,416,419,418,420,422,421,420,423,422,424,426,425,424,427,426,428,430,429,428,431,430,432,434,433,432,435,434,436,438,437,436,439,438,440,442,441,440,443,442,444,446,445,444,447,446,448,450,449,448,451,450,452,454,453,452,455,454,456,458,457,456,459,458,460,462,461,460,463,462,464,466,465,464,467,466,468,470,469,468,471,470,472,474,473,472,475,474,476,478,477,476,479,478,480,482,481,480,483,482,484,486,485,484,487,486,488,490,489,488,491,490,492,494,493,492,495,494,496,498,497,496,499,498,500,502,501,500,503,502,504,506,505,504,507,506,508,510,509,508,511,510,512,514,513,512,515,514,516,518,517,516,519,518,520,522,521,520,523,522,524,526,525,524,527,526,528,530,529,528,531,530,532,534,533,532,535,534,536,538,537,536,539,538,540,542,541,540,543,542,544,546,545,544,547,546,548,550,549,548,551,550,552,554,553,552,555,554,556,558,557,556,559,558,560,562,561,560,563,562,564,566,565,564,567,566,568,570,569,568,571,570,572,574,573,572,575,574,576,578,577,576,579,578,580,582,581,580,583,582,584,586,585,584,587,586,588,590,589,588,591,590,592,594,593,592,595,594,596,598,597,596,599,598,600,602,601,600,603,602,604,606,605,604,607,606,608,610,609,608,611,610,612,614,613,612,615,614,616,618,617,616,619,618,620,622,621,620,623,622,624,626,625,624,627,626,628,630,629,628,631,630,632,634,633,632,635,634,636,638,637,636,639,638,640,642,641,640,643,642,644,646,645,644,647,646,648,650,649,648,651,650,652,654,653,652,655,654,656,658,657,656,659,658,660,662,661,660,663,662,664,666,665,664,667,666,668,670,669,668,671,670,672,674,673,672,675,674,676,678,677,676,679,678,680,682,681,680,683,682,684,686,685,684,687,686,688,690,689,688,691,690,692,694,693,692,695,694,696,698,697,696,699,698,700,702,701,700,703,702,704,706,705,704,707,706,708,710,709,708,711,710,712,714,713,712,715,714,716,718,717,716,719,718,720,722,721,720,723,722,724,726,725,724,727,726,728,730,729,728,731,730,732,734,733,732,735,734,736,738,737,736,739,738,740,742,741,740,743,742,744,746,745,744,747,746,748,750,749,748,751,750,752,754,753,752,755,754,756,758,757,756,759,758,760,762,761,760,763,762,764,766,765,764,767,766,768,770,769,768,771,770,772,774,773,772,775,774,776,778,777,776,779,778,780,782,781,780,783,782,784,786,785,784,787,786,788,790,789,788,791,790,792,794,793,792,795,794,796,798,797,796,799,798,800,802,801,800,803,802,804,806,805,804,807,806,808,810,809,808,811,810,812,814,813,812,815,814,816,818,817,816,819,818,820,822,821,820,823,822,824,826,825,824,827,826,828,830,829,828,831,830,832,834,833,832,835,834,836,838,837,836,839,838,840,842,841,840,843,842,844,846,845,844,847,846,848,850,849,848,851,850,852,854,853,852,855,854,856,858,857,856,859,858,860,862,861,860,863,862,864,866,865,864,867,866,868,870,869,868,871,870,872,874,873,872,875,874,876,878,877,876,879,878,880,882,881,880,883,882,884,886,885,884,887,886,888,890,889,888,891,890,892,894,893,892,895,894,896,898,897,896,899,898,900,902,901,900,903,902,904,906,905,904,907,906,908,910,909,908,911,910,912,914,913,912,915,914,916,918,917,916,919,918,920,922,921,920,923,922,924,926,925,924,927,926,928,930,929,928,931,930,932,934,933,932,935,934,936,938,937,936,939,938,940,942,941,940,943,942,944,946,945,944,947,946,948,950,949,948,951,950,952,954,953,952,955,954,956,958,957,956,959,958,960,962,961,960,963,962,964,966,965,964,967,966,968,970,969,968,971,970,972,974,973,972,975,974,976,978,977,976,979,978,980,982,981,980,983,982,984,986,985,984,987,986,988,990,989,988,991,990,992,994,993,992,995,994,996,998,997,996,999,998,1000,1002,1001,1000,1003,1002,1004,1006,1005,1004,1007,1006,1008,1010,1009,1008,1011,1010,1012,1014,1013,1012,1015,1014,1016,1018,1017,1016,1019,1018,1020,1022,1021,1020,1023,1022,1024,1026,1025,1024,1027,1026,1028,1030,1029,1028,1031,1030,1032,1034,1033,1032,1035,1034,1036,1038,1037,1036,1039,1038]},{"tex":"cccloud:block/mainframe_leds_online","emit":true,"trans":false,"pos":[4,27,15.75,5,27,15.75,5,26,15.75,4,26,15.75,4,27,15.5,4,27,15.75,4,26,15.75,4,26,15.5,5,27,15.75,5,27,15.5,5,26,15.5,5,26,15.75,4,27,15.5,5,27,15.5,5,27,15.75,4,27,15.75,4,26,15.75,5,26,15.75,5,26,15.5,4,26,15.5,7.5,27,15.75,8.5,27,15.75,8.5,26,15.75,7.5,26,15.75,7.5,27,15.5,7.5,27,15.75,7.5,26,15.75,7.5,26,15.5,8.5,27,15.75,8.5,27,15.5,8.5,26,15.5,8.5,26,15.75,7.5,27,15.5,8.5,27,15.5,8.5,27,15.75,7.5,27,15.75,7.5,26,15.75,8.5,26,15.75,8.5,26,15.5,7.5,26,15.5,11,27,15.75,12,27,15.75,12,26,15.75,11,26,15.75,11,27,15.5,11,27,15.75,11,26,15.75,11,26,15.5,12,27,15.75,12,27,15.5,12,26,15.5,12,26,15.75,11,27,15.5,12,27,15.5,12,27,15.75,11,27,15.75,11,26,15.75,12,26,15.75,12,26,15.5,11,26,15.5,7,19.5,0.75,4,19.5,0.75,4,18.5,0.75,7,18.5,0.75,4,19.5,0.75,4,19.5,1,4,18.5,1,4,18.5,0.75,7,19.5,1,7,19.5,0.75,7,18.5,0.75,7,18.5,1,4,19.5,0.75,7,19.5,0.75,7,19.5,1,4,19.5,1,4,18.5,1,7,18.5,1,7,18.5,0.75,4,18.5,0.75,7,24,1.75,4,24,1.75,4,23,1.75,7,23,1.75,4,24,1.75,4,24,2,4,23,2,4,23,1.75,7,24,2,7,24,1.75,7,23,1.75,7,23,2,4,24,1.75,7,24,1.75,7,24,2,4,24,2,4,23,2,7,23,2,7,23,1.75,4,23,1.75,7,28.5,1.25,4,28.5,1.25,4,27.5,1.25,7,27.5,1.25,4,28.5,1.25,4,28.5,1.5,4,27.5,1.5,4,27.5,1.25,7,28.5,1.5,7,28.5,1.25,7,27.5,1.25,7,27.5,1.5,4,28.5,1.25,7,28.5,1.25,7,28.5,1.5,4,28.5,1.5,4,27.5,1.5,7,27.5,1.5,7,27.5,1.25,4,27.5,1.25,10,33,6,6,33,6,6,32,6,10,32,6,6,33,10,10,33,10,10,32,10,6,32,10,6,33,6,6,33,10,6,32,10,6,32,6,10,33,10,10,33,6,10,32,6,10,32,10,6,33,6,10,33,6,10,33,10,6,33,10,8,32.9,5.17,5.17,32.9,8,5.17,32,8,8,32,5.17,8,32.9,10.83,10.83,32.9,8,10.83,32,8,8,32,10.83,5.17,32.9,8,8,32.9,10.83,8,32,10.83,5.17,32,8,10.83,32.9,8,8,32.9,5.17,8,32,5.17,10.83,32,8,5.17,32.9,8,8,32.9,5.17,10.83,32.9,8,8,32.9,10.83,9,33.25,7,7,33.25,7,7,33,7,9,33,7,7,33.25,9,9,33.25,9,9,33,9,7,33,9,7,33.25,7,7,33.25,9,7,33,9,7,33,7,9,33.25,9,9,33.25,7,9,33,7,9,33,9,7,33.25,7,9,33.25,7,9,33.25,9,7,33.25,9,1.4,34.8,0.6,0.6,34.8,0.6,0.6,34,0.6,1.4,34,0.6,0.6,34.8,1.4,1.4,34.8,1.4,1.4,34,1.4,0.6,34,1.4,0.6,34.8,0.6,0.6,34.8,1.4,0.6,34,1.4,0.6,34,0.6,1.4,34.8,1.4,1.4,34.8,0.6,1.4,34,0.6,1.4,34,1.4,0.6,34.8,0.6,1.4,34.8,0.6,1.4,34.8,1.4,0.6,34.8,1.4,0.6,34,1.4,1.4,34,1.4,1.4,34,0.6,0.6,34,0.6,15.4,36,0.6,14.6,36,0.6,14.6,35.2,0.6,15.4,35.2,0.6,14.6,36,1.4,15.4,36,1.4,15.4,35.2,1.4,14.6,35.2,1.4,14.6,36,0.6,14.6,36,1.4,14.6,35.2,1.4,14.6,35.2,0.6,15.4,36,1.4,15.4,36,0.6,15.4,35.2,0.6,15.4,35.2,1.4,14.6,36,0.6,15.4,36,0.6,15.4,36,1.4,14.6,36,1.4,14.6,35.2,1.4,15.4,35.2,1.4,15.4,35.2,0.6,14.6,35.2,0.6,1.4,35.4,14.6,0.6,35.4,14.6,0.6,34.6,14.6,1.4,34.6,14.6,0.6,35.4,15.4,1.4,35.4,15.4,1.4,34.6,15.4,0.6,34.6,15.4,0.6,35.4,14.6,0.6,35.4,15.4,0.6,34.6,15.4,0.6,34.6,14.6,1.4,35.4,15.4,1.4,35.4,14.6,1.4,34.6,14.6,1.4,34.6,15.4,0.6,35.4,14.6,1.4,35.4,14.6,1.4,35.4,15.4,0.6,35.4,15.4,0.6,34.6,15.4,1.4,34.6,15.4,1.4,34.6,14.6,0.6,34.6,14.6,15.4,34.2,14.6,14.6,34.2,14.6,14.6,33.4,14.6,15.4,33.4,14.6,14.6,34.2,15.4,15.4,34.2,15.4,15.4,33.4,15.4,14.6,33.4,15.4,14.6,34.2,14.6,14.6,34.2,15.4,14.6,33.4,15.4,14.6,33.4,14.6,15.4,34.2,15.4,15.4,34.2,14.6,15.4,33.4,14.6,15.4,33.4,15.4,14.6,34.2,14.6,15.4,34.2,14.6,15.4,34.2,15.4,14.6,34.2,15.4,14.6,33.4,15.4,15.4,33.4,15.4,15.4,33.4,14.6,14.6,33.4,14.6],"uv":[0.625,0,0.6875,0,0.6875,0.0625,0.625,0.0625,0.625,0,0.6875,0,0.6875,0.0625,0.625,0.0625,0.625,0,0.6875,0,0.6875,0.0625,0.625,0.0625,0.625,0,0.6875,0,0.6875,0.0625,0.625,0.0625,0.625,0,0.6875,0,0.6875,0.0625,0.625,0.0625,0.625,0,0.6875,0,0.6875,0.0625,0.625,0.0625,0.625,0,0.6875,0,0.6875,0.0625,0.625,0.0625,0.625,0,0.6875,0,0.6875,0.0625,0.625,0.0625,0.625,0,0.6875,0,0.6875,0.0625,0.625,0.0625,0.625,0,0.6875,0,0.6875,0.0625,0.625,0.0625,0.625,0,0.6875,0,0.6875,0.0625,0.625,0.0625,0.625,0,0.6875,0,0.6875,0.0625,0.625,0.0625,0.625,0,0.6875,0,0.6875,0.0625,0.625,0.0625,0.625,0,0.6875,0,0.6875,0.0625,0.625,0.0625,0.625,0,0.6875,0,0.6875,0.0625,0.625,0.0625,0,0.1875,0.1875,0.1875,0.1875,0.25,0,0.25,0,0.1875,0.1875,0.1875,0.1875,0.25,0,0.25,0,0.1875,0.1875,0.1875,0.1875,0.25,0,0.25,0,0.1875,0.1875,0.1875,0.1875,0.25,0,0.25,0,0.1875,0.1875,0.1875,0.1875,0.25,0,0.25,0,0.25,0.1875,0.25,0.1875,0.3125,0,0.3125,0,0.25,0.1875,0.25,0.1875,0.3125,0,0.3125,0,0.25,0.1875,0.25,0.1875,0.3125,0,0.3125,0,0.25,0.1875,0.25,0.1875,0.3125,0,0.3125,0,0.25,0.1875,0.25,0.1875,0.3125,0,0.3125,0,0.3125,0.1875,0.3125,0.1875,0.375,0,0.375,0,0.3125,0.1875,0.3125,0.1875,0.375,0,0.375,0,0.3125,0.1875,0.3125,0.1875,0.375,0,0.375,0,0.3125,0.1875,0.3125,0.1875,0.375,0,0.375,0,0.3125,0.1875,0.3125,0.1875,0.375,0,0.375,0.25,0,0.5,0,0.5,0.25,0.25,0.25,0.25,0,0.5,0,0.5,0.25,0.25,0.25,0.25,0,0.5,0,0.5,0.25,0.25,0.25,0.25,0,0.5,0,0.5,0.25,0.25,0.25,0.25,0,0.5,0,0.5,0.25,0.25,0.25,0.25,0,0.5,0,0.5,0.25,0.25,0.25,0.25,0,0.5,0,0.5,0.25,0.25,0.25,0.25,0,0.5,0,0.5,0.25,0.25,0.25,0.25,0,0.5,0,0.5,0.25,0.25,0.25,0.25,0,0.5,0,0.5,0.25,0.25,0.25,0.5,0,0.625,0,0.625,0.125,0.5,0.125,0.5,0,0.625,0,0.625,0.125,0.5,0.125,0.5,0,0.625,0,0.625,0.125,0.5,0.125,0.5,0,0.625,0,0.625,0.125,0.5,0.125,0.5,0,0.625,0,0.625,0.125,0.5,0.125,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625,0.6875,0,0.75,0,0.75,0.0625,0.6875,0.0625],"shade":[1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22,24,26,25,24,27,26,28,30,29,28,31,30,32,34,33,32,35,34,36,38,37,36,39,38,40,42,41,40,43,42,44,46,45,44,47,46,48,50,49,48,51,50,52,54,53,52,55,54,56,58,57,56,59,58,60,62,61,60,63,62,64,66,65,64,67,66,68,70,69,68,71,70,72,74,73,72,75,74,76,78,77,76,79,78,80,82,81,80,83,82,84,86,85,84,87,86,88,90,89,88,91,90,92,94,93,92,95,94,96,98,97,96,99,98,100,102,101,100,103,102,104,106,105,104,107,106,108,110,109,108,111,110,112,114,113,112,115,114,116,118,117,116,119,118,120,122,121,120,123,122,124,126,125,124,127,126,128,130,129,128,131,130,132,134,133,132,135,134,136,138,137,136,139,138,140,142,141,140,143,142,144,146,145,144,147,146,148,150,149,148,151,150,152,154,153,152,155,154,156,158,157,156,159,158,160,162,161,160,163,162,164,166,165,164,167,166,168,170,169,168,171,170,172,174,173,172,175,174,176,178,177,176,179,178,180,182,181,180,183,182,184,186,185,184,187,186,188,190,189,188,191,190,192,194,193,192,195,194,196,198,197,196,199,198,200,202,201,200,203,202,204,206,205,204,207,206,208,210,209,208,211,210,212,214,213,212,215,214,216,218,217,216,219,218,220,222,221,220,223,222,224,226,225,224,227,226,228,230,229,228,231,230,232,234,233,232,235,234,236,238,237,236,239,238,240,242,241,240,243,242,244,246,245,244,247,246,248,250,249,248,251,250,252,254,253,252,255,254,256,258,257,256,259,258,260,262,261,260,263,262,264,266,265,264,267,266,268,270,269,268,271,270,272,274,273,272,275,274]},{"tex":"cccloud:block/mainframe_glass","emit":false,"trans":true,"pos":[13,29,0.5,3,29,0.5,3,16,0.5,13,16,0.5],"uv":[0.1875,0.1875,0.8125,0.1875,0.8125,1,0.1875,1],"shade":[0.8],"idx":[0,2,1,0,3,2]}],"tall":2,"status":{"online":{},"connecting":{"cccloud:block/mainframe_leds_online":"cccloud:block/mainframe_leds_connecting"},"offline":{"cccloud:block/mainframe_leds_online":"cccloud:block/mainframe_leds_offline"}}},"relay":{"parts":[{"tex":"cccloud:block/relay","emit":false,"trans":false,"pos":[14,6,2,2,6,2,2,0,2,14,0,2,2,6,14,14,6,14,14,0,14,2,0,14,2,6,2,2,6,14,2,0,14,2,0,2,14,6,14,14,6,2,14,0,2,14,0,14,2,6,2,14,6,2,14,6,14,2,6,14,2,0,14,14,0,14,14,0,2,2,0,2,12,7,4,4,7,4,4,6,4,12,6,4,4,7,12,12,7,12,12,6,12,4,6,12,4,7,4,4,7,12,4,6,12,4,6,4,12,7,12,12,7,4,12,6,4,12,6,12,4,7,4,12,7,4,12,7,12,4,7,12,8.5,12.6,7.5,7.5,12.6,7.5,7.5,7,7.5,8.5,7,7.5,7.5,12.6,8.5,8.5,12.6,8.5,8.5,7,8.5,7.5,7,8.5,7.5,12.6,7.5,7.5,12.6,8.5,7.5,7,8.5,7.5,7,7.5,8.5,12.6,8.5,8.5,12.6,7.5,8.5,7,7.5,8.5,7,8.5,7.5,12.6,7.5,8.5,12.6,7.5,8.5,12.6,8.5,7.5,12.6,8.5,9,9.5,7,7,9.5,7,7,9,7,9,9,7,7,9.5,9,9,9.5,9,9,9,9,7,9,9,7,9.5,7,7,9.5,9,7,9,9,7,9,7,9,9.5,9,9,9.5,7,9,9,7,9,9,9,7,9.5,7,9,9.5,7,9,9.5,9,7,9.5,9,7,9,9,9,9,9,9,9,7,7,9,7,8.25,15.2,7.75,7.75,15.2,7.75,7.75,13.4,7.75,8.25,13.4,7.75,7.75,15.2,8.25,8.25,15.2,8.25,8.25,13.4,8.25,7.75,13.4,8.25,7.75,15.2,7.75,7.75,15.2,8.25,7.75,13.4,8.25,7.75,13.4,7.75,8.25,15.2,8.25,8.25,15.2,7.75,8.25,13.4,7.75,8.25,13.4,8.25,7.75,15.2,7.75,8.25,15.2,7.75,8.25,15.2,8.25,7.75,15.2,8.25],"uv":[0.0313,0.1563,0.2188,0.1563,0.2188,0.25,0.0313,0.25,0.0313,0.1563,0.2188,0.1563,0.2188,0.25,0.0313,0.25,0.0313,0.1563,0.2188,0.1563,0.2188,0.25,0.0313,0.25,0.0313,0.1563,0.2188,0.1563,0.2188,0.25,0.0313,0.25,0.2813,0.0313,0.4688,0.0313,0.4688,0.2188,0.2813,0.2188,0.0313,0.2813,0.2188,0.2813,0.2188,0.4688,0.0313,0.4688,0.8125,0.1406,0.9375,0.1406,0.9375,0.1563,0.8125,0.1563,0.8125,0.1406,0.9375,0.1406,0.9375,0.1563,0.8125,0.1563,0.8125,0.1406,0.9375,0.1406,0.9375,0.1563,0.8125,0.1563,0.8125,0.1406,0.9375,0.1406,0.9375,0.1563,0.8125,0.1563,0.3125,0.0625,0.4375,0.0625,0.4375,0.1875,0.3125,0.1875,0.1172,0.3031,0.1328,0.3031,0.1328,0.3906,0.1172,0.3906,0.1172,0.3031,0.1328,0.3031,0.1328,0.3906,0.1172,0.3906,0.1172,0.3031,0.1328,0.3031,0.1328,0.3906,0.1172,0.3906,0.1172,0.3031,0.1328,0.3031,0.1328,0.3906,0.1172,0.3906,0.1172,0.3672,0.1328,0.3672,0.1328,0.3828,0.1172,0.3828,0.6094,0.1016,0.6406,0.1016,0.6406,0.1094,0.6094,0.1094,0.6094,0.1016,0.6406,0.1016,0.6406,0.1094,0.6094,0.1094,0.6094,0.1016,0.6406,0.1016,0.6406,0.1094,0.6094,0.1094,0.6094,0.1016,0.6406,0.1016,0.6406,0.1094,0.6094,0.1094,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.1211,0.2625,0.1289,0.2625,0.1289,0.2906,0.1211,0.2906,0.1211,0.2625,0.1289,0.2625,0.1289,0.2906,0.1211,0.2906,0.1211,0.2625,0.1289,0.2625,0.1289,0.2906,0.1211,0.2906,0.1211,0.2625,0.1289,0.2625,0.1289,0.2906,0.1211,0.2906,0.1211,0.3711,0.1289,0.3711,0.1289,0.3789,0.1211,0.3789],"shade":[0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.8,0.8,0.6,0.6,1,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22,24,26,25,24,27,26,28,30,29,28,31,30,32,34,33,32,35,34,36,38,37,36,39,38,40,42,41,40,43,42,44,46,45,44,47,46,48,50,49,48,51,50,52,54,53,52,55,54,56,58,57,56,59,58,60,62,61,60,63,62,64,66,65,64,67,66,68,70,69,68,71,70,72,74,73,72,75,74,76,78,77,76,79,78,80,82,81,80,83,82,84,86,85,84,87,86,88,90,89,88,91,90,92,94,93,92,95,94,96,98,97,96,99,98,100,102,101,100,103,102,104,106,105,104,107,106]},{"tex":"cccloud:block/relay_status_online","emit":true,"trans":false,"pos":[11,5,1.9,5,5,1.9,5,4,1.9,11,4,1.9,5,5,1.9,5,5,2,5,4,2,5,4,1.9,11,5,2,11,5,1.9,11,4,1.9,11,4,2,5,5,1.9,11,5,1.9,11,5,2,5,5,2,5,4,2,11,4,2,11,4,1.9,5,4,1.9,5,5,14.1,11,5,14.1,11,4,14.1,5,4,14.1,5,5,14,5,5,14.1,5,4,14.1,5,4,14,11,5,14.1,11,5,14,11,4,14,11,4,14.1,5,5,14,11,5,14,11,5,14.1,5,5,14.1,5,4,14.1,11,4,14.1,11,4,14,5,4,14,2,5,5,1.9,5,5,1.9,4,5,2,4,5,1.9,5,11,2,5,11,2,4,11,1.9,4,11,1.9,5,5,1.9,5,11,1.9,4,11,1.9,4,5,1.9,5,5,2,5,5,2,5,11,1.9,5,11,1.9,4,11,2,4,11,2,4,5,1.9,4,5,14.1,5,5,14,5,5,14,4,5,14.1,4,5,14,5,11,14.1,5,11,14.1,4,11,14,4,11,14.1,5,11,14.1,5,5,14.1,4,5,14.1,4,11,14,5,5,14.1,5,5,14.1,5,11,14,5,11,14,4,11,14.1,4,11,14.1,4,5,14,4,5,8.4,16,7.6,7.6,16,7.6,7.6,15.2,7.6,8.4,15.2,7.6,7.6,16,8.4,8.4,16,8.4,8.4,15.2,8.4,7.6,15.2,8.4,7.6,16,7.6,7.6,16,8.4,7.6,15.2,8.4,7.6,15.2,7.6,8.4,16,8.4,8.4,16,7.6,8.4,15.2,7.6,8.4,15.2,8.4,7.6,16,7.6,8.4,16,7.6,8.4,16,8.4,7.6,16,8.4,7.6,15.2,8.4,8.4,15.2,8.4,8.4,15.2,7.6,7.6,15.2,7.6],"uv":[0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0.125,0.0625,0.125,0.0625,0.1875,0,0.1875,0,0.125,0.0625,0.125,0.0625,0.1875,0,0.1875,0,0.125,0.0625,0.125,0.0625,0.1875,0,0.1875,0,0.125,0.0625,0.125,0.0625,0.1875,0,0.1875,0,0.125,0.0625,0.125,0.0625,0.1875,0,0.1875,0,0.125,0.0625,0.125,0.0625,0.1875,0,0.1875],"shade":[1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22,24,26,25,24,27,26,28,30,29,28,31,30,32,34,33,32,35,34,36,38,37,36,39,38,40,42,41,40,43,42,44,46,45,44,47,46,48,50,49,48,51,50,52,54,53,52,55,54,56,58,57,56,59,58,60,62,61,60,63,62,64,66,65,64,67,66,68,70,69,68,71,70,72,74,73,72,75,74,76,78,77,76,79,78,80,82,81,80,83,82,84,86,85,84,87,86,88,90,89,88,91,90,92,94,93,92,95,94,96,98,97,96,99,98,100,102,101,100,103,102]},{"tex":"cccloud:block/relay","emit":false,"trans":false,"pos":[11,13.5,3,5,13.5,3,5,12.5,3,11,12.5,3,5,13.5,4,11,13.5,4,11,12.5,4,5,12.5,4,5,13.5,3,5,13.5,4,5,12.5,4,5,12.5,3,11,13.5,4,11,13.5,3,11,12.5,3,11,12.5,4,5,13.5,3,11,13.5,3,11,13.5,4,5,13.5,4,5,12.5,4,11,12.5,4,11,12.5,3,5,12.5,3,11,13.5,12,5,13.5,12,5,12.5,12,11,12.5,12,5,13.5,13,11,13.5,13,11,12.5,13,5,12.5,13,5,13.5,12,5,13.5,13,5,12.5,13,5,12.5,12,11,13.5,13,11,13.5,12,11,12.5,12,11,12.5,13,5,13.5,12,11,13.5,12,11,13.5,13,5,13.5,13,5,12.5,13,11,12.5,13,11,12.5,12,5,12.5,12,4,13.5,5,3,13.5,5,3,12.5,5,4,12.5,5,3,13.5,11,4,13.5,11,4,12.5,11,3,12.5,11,3,13.5,5,3,13.5,11,3,12.5,11,3,12.5,5,4,13.5,11,4,13.5,5,4,12.5,5,4,12.5,11,3,13.5,5,4,13.5,5,4,13.5,11,3,13.5,11,3,12.5,11,4,12.5,11,4,12.5,5,3,12.5,5,13,13.5,5,12,13.5,5,12,12.5,5,13,12.5,5,12,13.5,11,13,13.5,11,13,12.5,11,12,12.5,11,12,13.5,5,12,13.5,11,12,12.5,11,12,12.5,5,13,13.5,11,13,13.5,5,13,12.5,5,13,12.5,11,12,13.5,5,13,13.5,5,13,13.5,11,12,13.5,11,12,12.5,11,13,12.5,11,13,12.5,5,12,12.5,5,4.96,13.4,2.84,2.84,13.4,4.96,2.84,12.6,4.96,4.96,12.6,2.84,3.54,13.4,5.66,5.66,13.4,3.54,5.66,12.6,3.54,3.54,12.6,5.66,2.84,13.4,4.96,3.54,13.4,5.66,3.54,12.6,5.66,2.84,12.6,4.96,5.66,13.4,3.54,4.96,13.4,2.84,4.96,12.6,2.84,5.66,12.6,3.54,2.84,13.4,4.96,4.96,13.4,2.84,5.66,13.4,3.54,3.54,13.4,5.66,3.54,12.6,5.66,5.66,12.6,3.54,4.96,12.6,2.84,2.84,12.6,4.96,13.16,13.4,4.96,11.04,13.4,2.84,11.04,12.6,2.84,13.16,12.6,4.96,10.34,13.4,3.54,12.46,13.4,5.66,12.46,12.6,5.66,10.34,12.6,3.54,11.04,13.4,2.84,10.34,13.4,3.54,10.34,12.6,3.54,11.04,12.6,2.84,12.46,13.4,5.66,13.16,13.4,4.96,13.16,12.6,4.96,12.46,12.6,5.66,11.04,13.4,2.84,13.16,13.4,4.96,12.46,13.4,5.66,10.34,13.4,3.54,10.34,12.6,3.54,12.46,12.6,5.66,13.16,12.6,4.96,11.04,12.6,2.84,5.66,13.4,12.46,3.54,13.4,10.34,3.54,12.6,10.34,5.66,12.6,12.46,2.84,13.4,11.04,4.96,13.4,13.16,4.96,12.6,13.16,2.84,12.6,11.04,3.54,13.4,10.34,2.84,13.4,11.04,2.84,12.6,11.04,3.54,12.6,10.34,4.96,13.4,13.16,5.66,13.4,12.46,5.66,12.6,12.46,4.96,12.6,13.16,3.54,13.4,10.34,5.66,13.4,12.46,4.96,13.4,13.16,2.84,13.4,11.04,2.84,12.6,11.04,4.96,12.6,13.16,5.66,12.6,12.46,3.54,12.6,10.34,12.46,13.4,10.34,10.34,13.4,12.46,10.34,12.6,12.46,12.46,12.6,10.34,11.04,13.4,13.16,13.16,13.4,11.04,13.16,12.6,11.04,11.04,12.6,13.16,10.34,13.4,12.46,11.04,13.4,13.16,11.04,12.6,13.16,10.34,12.6,12.46,13.16,13.4,11.04,12.46,13.4,10.34,12.46,12.6,10.34,13.16,12.6,11.04,10.34,13.4,12.46,12.46,13.4,10.34,13.16,13.4,11.04,11.04,13.4,13.16,11.04,12.6,13.16,13.16,12.6,11.04,12.46,12.6,10.34,10.34,12.6,12.46,8.6,13.4,7.4,7.4,13.4,7.4,7.4,12.6,7.4,8.6,12.6,7.4,7.4,13.4,8.6,8.6,13.4,8.6,8.6,12.6,8.6,7.4,12.6,8.6,7.4,13.4,7.4,7.4,13.4,8.6,7.4,12.6,8.6,7.4,12.6,7.4,8.6,13.4,8.6,8.6,13.4,7.4,8.6,12.6,7.4,8.6,12.6,8.6,7.4,13.4,7.4,8.6,13.4,7.4,8.6,13.4,8.6,7.4,13.4,8.6,7.4,12.6,8.6,8.6,12.6,8.6,8.6,12.6,7.4,7.4,12.6,7.4,12,13.2,7.8,4,13.2,7.8,4,12.8,7.8,12,12.8,7.8,4,13.2,8.2,12,13.2,8.2,12,12.8,8.2,4,12.8,8.2,4,13.2,7.8,12,13.2,7.8,12,13.2,8.2,4,13.2,8.2,4,12.8,8.2,12,12.8,8.2,12,12.8,7.8,4,12.8,7.8,7.85,13.15,4,7.85,13.15,12,7.85,12.85,12,7.85,12.85,4,8.15,13.15,12,8.15,13.15,4,8.15,12.85,4,8.15,12.85,12,7.85,13.15,4,8.15,13.15,4,8.15,13.15,12,7.85,13.15,12,7.85,12.85,12,8.15,12.85,12,8.15,12.85,4,7.85,12.85,4],"uv":[0.3281,0.2891,0.4219,0.2891,0.4219,0.3047,0.3281,0.3047,0.3281,0.2891,0.4219,0.2891,0.4219,0.3047,0.3281,0.3047,0.2969,0.2891,0.3125,0.2891,0.3125,0.3047,0.2969,0.3047,0.4375,0.2891,0.4531,0.2891,0.4531,0.3047,0.4375,0.3047,0.8281,0.0469,0.9219,0.0469,0.9219,0.0625,0.8281,0.0625,0.3281,0.4375,0.4219,0.4375,0.4219,0.4531,0.3281,0.4531,0.3281,0.2891,0.4219,0.2891,0.4219,0.3047,0.3281,0.3047,0.3281,0.2891,0.4219,0.2891,0.4219,0.3047,0.3281,0.3047,0.4375,0.2891,0.4531,0.2891,0.4531,0.3047,0.4375,0.3047,0.2969,0.2891,0.3125,0.2891,0.3125,0.3047,0.2969,0.3047,0.8281,0.1875,0.9219,0.1875,0.9219,0.2031,0.8281,0.2031,0.3281,0.2969,0.4219,0.2969,0.4219,0.3125,0.3281,0.3125,0.4375,0.2891,0.4531,0.2891,0.4531,0.3047,0.4375,0.3047,0.2969,0.2891,0.3125,0.2891,0.3125,0.3047,0.2969,0.3047,0.3281,0.2891,0.4219,0.2891,0.4219,0.3047,0.3281,0.3047,0.3281,0.2891,0.4219,0.2891,0.4219,0.3047,0.3281,0.3047,0.7969,0.0781,0.8125,0.0781,0.8125,0.1719,0.7969,0.1719,0.2969,0.3281,0.3125,0.3281,0.3125,0.4219,0.2969,0.4219,0.2969,0.2891,0.3125,0.2891,0.3125,0.3047,0.2969,0.3047,0.4375,0.2891,0.4531,0.2891,0.4531,0.3047,0.4375,0.3047,0.3281,0.2891,0.4219,0.2891,0.4219,0.3047,0.3281,0.3047,0.3281,0.2891,0.4219,0.2891,0.4219,0.3047,0.3281,0.3047,0.9375,0.0781,0.9531,0.0781,0.9531,0.1719,0.9375,0.1719,0.4375,0.3281,0.4531,0.3281,0.4531,0.4219,0.4375,0.4219,0.4102,0.2906,0.457,0.2906,0.457,0.3031,0.4102,0.3031,0.293,0.2906,0.3398,0.2906,0.3398,0.3031,0.293,0.3031,0.3086,0.2906,0.3242,0.2906,0.3242,0.3031,0.3086,0.3031,0.4258,0.2906,0.4414,0.2906,0.4414,0.3031,0.4258,0.3031,0.793,0.0586,0.8398,0.0586,0.8398,0.0742,0.793,0.0742,0.293,0.4258,0.3398,0.4258,0.3398,0.4414,0.293,0.4414,0.293,0.2906,0.3398,0.2906,0.3398,0.3031,0.293,0.3031,0.4102,0.2906,0.457,0.2906,0.457,0.3031,0.4102,0.3031,0.3086,0.2906,0.3242,0.2906,0.3242,0.3031,0.3086,0.3031,0.4258,0.2906,0.4414,0.2906,0.4414,0.3031,0.4258,0.3031,0.9102,0.0586,0.957,0.0586,0.957,0.0742,0.9102,0.0742,0.4102,0.4258,0.457,0.4258,0.457,0.4414,0.4102,0.4414,0.4102,0.2906,0.457,0.2906,0.457,0.3031,0.4102,0.3031,0.293,0.2906,0.3398,0.2906,0.3398,0.3031,0.293,0.3031,0.4258,0.2906,0.4414,0.2906,0.4414,0.3031,0.4258,0.3031,0.3086,0.2906,0.3242,0.2906,0.3242,0.3031,0.3086,0.3031,0.793,0.1758,0.8398,0.1758,0.8398,0.1914,0.793,0.1914,0.293,0.3086,0.3398,0.3086,0.3398,0.3242,0.293,0.3242,0.293,0.2906,0.3398,0.2906,0.3398,0.3031,0.293,0.3031,0.4102,0.2906,0.457,0.2906,0.457,0.3031,0.4102,0.3031,0.4258,0.2906,0.4414,0.2906,0.4414,0.3031,0.4258,0.3031,0.3086,0.2906,0.3242,0.2906,0.3242,0.3031,0.3086,0.3031,0.9102,0.1758,0.957,0.1758,0.957,0.1914,0.9102,0.1914,0.4102,0.3086,0.457,0.3086,0.457,0.3242,0.4102,0.3242,0.1156,0.2906,0.1344,0.2906,0.1344,0.3031,0.1156,0.3031,0.1156,0.2906,0.1344,0.2906,0.1344,0.3031,0.1156,0.3031,0.1156,0.2906,0.1344,0.2906,0.1344,0.3031,0.1156,0.3031,0.1156,0.2906,0.1344,0.2906,0.1344,0.3031,0.1156,0.3031,0.1156,0.3656,0.1344,0.3656,0.1344,0.3844,0.1156,0.3844,0.1156,0.3656,0.1344,0.3656,0.1344,0.3844,0.1156,0.3844,0.0625,0.2938,0.1875,0.2938,0.1875,0.3,0.0625,0.3,0.0625,0.2938,0.1875,0.2938,0.1875,0.3,0.0625,0.3,0.0625,0.3719,0.1875,0.3719,0.1875,0.3781,0.0625,0.3781,0.0625,0.3719,0.1875,0.3719,0.1875,0.3781,0.0625,0.3781,0.0625,0.2945,0.1875,0.2945,0.1875,0.2992,0.0625,0.2992,0.0625,0.2945,0.1875,0.2945,0.1875,0.2992,0.0625,0.2992,0.1227,0.3125,0.1273,0.3125,0.1273,0.4375,0.1227,0.4375,0.1227,0.3125,0.1273,0.3125,0.1273,0.4375,0.1227,0.4375],"shade":[0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.7,0.7,0.7,0.7,1,0.5,0.7,0.7,0.7,0.7,1,0.5,0.7,0.7,0.7,0.7,1,0.5,0.7,0.7,0.7,0.7,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,1,0.5,0.6,0.6,1,0.5],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22,24,26,25,24,27,26,28,30,29,28,31,30,32,34,33,32,35,34,36,38,37,36,39,38,40,42,41,40,43,42,44,46,45,44,47,46,48,50,49,48,51,50,52,54,53,52,55,54,56,58,57,56,59,58,60,62,61,60,63,62,64,66,65,64,67,66,68,70,69,68,71,70,72,74,73,72,75,74,76,78,77,76,79,78,80,82,81,80,83,82,84,86,85,84,87,86,88,90,89,88,91,90,92,94,93,92,95,94,96,98,97,96,99,98,100,102,101,100,103,102,104,106,105,104,107,106,108,110,109,108,111,110,112,114,113,112,115,114,116,118,117,116,119,118,120,122,121,120,123,122,124,126,125,124,127,126,128,130,129,128,131,130,132,134,133,132,135,134,136,138,137,136,139,138,140,142,141,140,143,142,144,146,145,144,147,146,148,150,149,148,151,150,152,154,153,152,155,154,156,158,157,156,159,158,160,162,161,160,163,162,164,166,165,164,167,166,168,170,169,168,171,170,172,174,173,172,175,174,176,178,177,176,179,178,180,182,181,180,183,182,184,186,185,184,187,186,188,190,189,188,191,190,192,194,193,192,195,194,196,198,197,196,199,198,200,202,201,200,203,202,204,206,205,204,207,206,208,210,209,208,211,210,212,214,213,212,215,214,216,218,217,216,219,218,220,222,221,220,223,222,224,226,225,224,227,226,228,230,229,228,231,230,232,234,233,232,235,234,236,238,237,236,239,238,240,242,241,240,243,242,244,246,245,244,247,246]},{"tex":"cccloud:block/relay","emit":true,"trans":false,"pos":[8.5,13.4,2.7,7.5,13.4,2.7,7.5,12.6,2.7,8.5,12.6,2.7,7.5,13.4,2.7,7.5,13.4,3,7.5,12.6,3,7.5,12.6,2.7,8.5,13.4,3,8.5,13.4,2.7,8.5,12.6,2.7,8.5,12.6,3,7.5,13.4,2.7,8.5,13.4,2.7,8.5,13.4,3,7.5,13.4,3,7.5,12.6,3,8.5,12.6,3,8.5,12.6,2.7,7.5,12.6,2.7,7.5,13.4,13.3,8.5,13.4,13.3,8.5,12.6,13.3,7.5,12.6,13.3,7.5,13.4,13,7.5,13.4,13.3,7.5,12.6,13.3,7.5,12.6,13,8.5,13.4,13.3,8.5,13.4,13,8.5,12.6,13,8.5,12.6,13.3,7.5,13.4,13,8.5,13.4,13,8.5,13.4,13.3,7.5,13.4,13.3,7.5,12.6,13.3,8.5,12.6,13.3,8.5,12.6,13,7.5,12.6,13],"uv":[0.6172,0.2906,0.6328,0.2906,0.6328,0.3031,0.6172,0.3031,0.5422,0.2906,0.5469,0.2906,0.5469,0.3031,0.5422,0.3031,0.7031,0.2906,0.7078,0.2906,0.7078,0.3031,0.7031,0.3031,0.6172,0.2922,0.6328,0.2922,0.6328,0.2969,0.6172,0.2969,0.6172,0.4531,0.6328,0.4531,0.6328,0.4578,0.6172,0.4578,0.6172,0.2906,0.6328,0.2906,0.6328,0.3031,0.6172,0.3031,0.7031,0.2906,0.7078,0.2906,0.7078,0.3031,0.7031,0.3031,0.5422,0.2906,0.5469,0.2906,0.5469,0.3031,0.5422,0.3031,0.6172,0.4531,0.6328,0.4531,0.6328,0.4578,0.6172,0.4578,0.6172,0.2922,0.6328,0.2922,0.6328,0.2969,0.6172,0.2969],"shade":[1,1,1,1,1,1,1,1,1,1],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22,24,26,25,24,27,26,28,30,29,28,31,30,32,34,33,32,35,34,36,38,37,36,39,38]}],"tall":1,"status":{"online":{},"connecting":{"cccloud:block/relay_status_online":"cccloud:block/relay_status_connecting"},"offline":{"cccloud:block/relay_status_online":"cccloud:block/relay_status_offline"},"no_power":{"cccloud:block/relay_status_online":"cccloud:block/relay_status_no_power"}}},"advanced_relay":{"parts":[{"tex":"cccloud:block/relay_advanced","emit":false,"trans":false,"pos":[14,6,2,2,6,2,2,0,2,14,0,2,2,6,14,14,6,14,14,0,14,2,0,14,2,6,2,2,6,14,2,0,14,2,0,2,14,6,14,14,6,2,14,0,2,14,0,14,2,6,2,14,6,2,14,6,14,2,6,14,2,0,14,14,0,14,14,0,2,2,0,2,12,7,4,4,7,4,4,6,4,12,6,4,4,7,12,12,7,12,12,6,12,4,6,12,4,7,4,4,7,12,4,6,12,4,6,4,12,7,12,12,7,4,12,6,4,12,6,12,4,7,4,12,7,4,12,7,12,4,7,12,8.5,12.6,7.5,7.5,12.6,7.5,7.5,7,7.5,8.5,7,7.5,7.5,12.6,8.5,8.5,12.6,8.5,8.5,7,8.5,7.5,7,8.5,7.5,12.6,7.5,7.5,12.6,8.5,7.5,7,8.5,7.5,7,7.5,8.5,12.6,8.5,8.5,12.6,7.5,8.5,7,7.5,8.5,7,8.5,7.5,12.6,7.5,8.5,12.6,7.5,8.5,12.6,8.5,7.5,12.6,8.5,9,9.5,7,7,9.5,7,7,9,7,9,9,7,7,9.5,9,9,9.5,9,9,9,9,7,9,9,7,9.5,7,7,9.5,9,7,9,9,7,9,7,9,9.5,9,9,9.5,7,9,9,7,9,9,9,7,9.5,7,9,9.5,7,9,9.5,9,7,9.5,9,7,9,9,9,9,9,9,9,7,7,9,7,8.25,15.2,7.75,7.75,15.2,7.75,7.75,13.4,7.75,8.25,13.4,7.75,7.75,15.2,8.25,8.25,15.2,8.25,8.25,13.4,8.25,7.75,13.4,8.25,7.75,15.2,7.75,7.75,15.2,8.25,7.75,13.4,8.25,7.75,13.4,7.75,8.25,15.2,8.25,8.25,15.2,7.75,8.25,13.4,7.75,8.25,13.4,8.25,7.75,15.2,7.75,8.25,15.2,7.75,8.25,15.2,8.25,7.75,15.2,8.25],"uv":[0.0313,0.1563,0.2188,0.1563,0.2188,0.25,0.0313,0.25,0.0313,0.1563,0.2188,0.1563,0.2188,0.25,0.0313,0.25,0.0313,0.1563,0.2188,0.1563,0.2188,0.25,0.0313,0.25,0.0313,0.1563,0.2188,0.1563,0.2188,0.25,0.0313,0.25,0.2813,0.0313,0.4688,0.0313,0.4688,0.2188,0.2813,0.2188,0.0313,0.2813,0.2188,0.2813,0.2188,0.4688,0.0313,0.4688,0.8125,0.1406,0.9375,0.1406,0.9375,0.1563,0.8125,0.1563,0.8125,0.1406,0.9375,0.1406,0.9375,0.1563,0.8125,0.1563,0.8125,0.1406,0.9375,0.1406,0.9375,0.1563,0.8125,0.1563,0.8125,0.1406,0.9375,0.1406,0.9375,0.1563,0.8125,0.1563,0.3125,0.0625,0.4375,0.0625,0.4375,0.1875,0.3125,0.1875,0.1172,0.3031,0.1328,0.3031,0.1328,0.3906,0.1172,0.3906,0.1172,0.3031,0.1328,0.3031,0.1328,0.3906,0.1172,0.3906,0.1172,0.3031,0.1328,0.3031,0.1328,0.3906,0.1172,0.3906,0.1172,0.3031,0.1328,0.3031,0.1328,0.3906,0.1172,0.3906,0.1172,0.3672,0.1328,0.3672,0.1328,0.3828,0.1172,0.3828,0.6094,0.1016,0.6406,0.1016,0.6406,0.1094,0.6094,0.1094,0.6094,0.1016,0.6406,0.1016,0.6406,0.1094,0.6094,0.1094,0.6094,0.1016,0.6406,0.1016,0.6406,0.1094,0.6094,0.1094,0.6094,0.1016,0.6406,0.1016,0.6406,0.1094,0.6094,0.1094,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.1211,0.2625,0.1289,0.2625,0.1289,0.2906,0.1211,0.2906,0.1211,0.2625,0.1289,0.2625,0.1289,0.2906,0.1211,0.2906,0.1211,0.2625,0.1289,0.2625,0.1289,0.2906,0.1211,0.2906,0.1211,0.2625,0.1289,0.2625,0.1289,0.2906,0.1211,0.2906,0.1211,0.3711,0.1289,0.3711,0.1289,0.3789,0.1211,0.3789],"shade":[0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.8,0.8,0.6,0.6,1,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22,24,26,25,24,27,26,28,30,29,28,31,30,32,34,33,32,35,34,36,38,37,36,39,38,40,42,41,40,43,42,44,46,45,44,47,46,48,50,49,48,51,50,52,54,53,52,55,54,56,58,57,56,59,58,60,62,61,60,63,62,64,66,65,64,67,66,68,70,69,68,71,70,72,74,73,72,75,74,76,78,77,76,79,78,80,82,81,80,83,82,84,86,85,84,87,86,88,90,89,88,91,90,92,94,93,92,95,94,96,98,97,96,99,98,100,102,101,100,103,102,104,106,105,104,107,106]},{"tex":"cccloud:block/relay_status_online","emit":true,"trans":false,"pos":[11,5,1.9,5,5,1.9,5,4,1.9,11,4,1.9,5,5,1.9,5,5,2,5,4,2,5,4,1.9,11,5,2,11,5,1.9,11,4,1.9,11,4,2,5,5,1.9,11,5,1.9,11,5,2,5,5,2,5,4,2,11,4,2,11,4,1.9,5,4,1.9,5,5,14.1,11,5,14.1,11,4,14.1,5,4,14.1,5,5,14,5,5,14.1,5,4,14.1,5,4,14,11,5,14.1,11,5,14,11,4,14,11,4,14.1,5,5,14,11,5,14,11,5,14.1,5,5,14.1,5,4,14.1,11,4,14.1,11,4,14,5,4,14,2,5,5,1.9,5,5,1.9,4,5,2,4,5,1.9,5,11,2,5,11,2,4,11,1.9,4,11,1.9,5,5,1.9,5,11,1.9,4,11,1.9,4,5,1.9,5,5,2,5,5,2,5,11,1.9,5,11,1.9,4,11,2,4,11,2,4,5,1.9,4,5,14.1,5,5,14,5,5,14,4,5,14.1,4,5,14,5,11,14.1,5,11,14.1,4,11,14,4,11,14.1,5,11,14.1,5,5,14.1,4,5,14.1,4,11,14,5,5,14.1,5,5,14.1,5,11,14,5,11,14,4,11,14.1,4,11,14.1,4,5,14,4,5,8.4,16,7.6,7.6,16,7.6,7.6,15.2,7.6,8.4,15.2,7.6,7.6,16,8.4,8.4,16,8.4,8.4,15.2,8.4,7.6,15.2,8.4,7.6,16,7.6,7.6,16,8.4,7.6,15.2,8.4,7.6,15.2,7.6,8.4,16,8.4,8.4,16,7.6,8.4,15.2,7.6,8.4,15.2,8.4,7.6,16,7.6,8.4,16,7.6,8.4,16,8.4,7.6,16,8.4,7.6,15.2,8.4,8.4,15.2,8.4,8.4,15.2,7.6,7.6,15.2,7.6],"uv":[0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0.125,0.0625,0.125,0.0625,0.1875,0,0.1875,0,0.125,0.0625,0.125,0.0625,0.1875,0,0.1875,0,0.125,0.0625,0.125,0.0625,0.1875,0,0.1875,0,0.125,0.0625,0.125,0.0625,0.1875,0,0.1875,0,0.125,0.0625,0.125,0.0625,0.1875,0,0.1875,0,0.125,0.0625,0.125,0.0625,0.1875,0,0.1875],"shade":[1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22,24,26,25,24,27,26,28,30,29,28,31,30,32,34,33,32,35,34,36,38,37,36,39,38,40,42,41,40,43,42,44,46,45,44,47,46,48,50,49,48,51,50,52,54,53,52,55,54,56,58,57,56,59,58,60,62,61,60,63,62,64,66,65,64,67,66,68,70,69,68,71,70,72,74,73,72,75,74,76,78,77,76,79,78,80,82,81,80,83,82,84,86,85,84,87,86,88,90,89,88,91,90,92,94,93,92,95,94,96,98,97,96,99,98,100,102,101,100,103,102]},{"tex":"cccloud:block/relay_advanced","emit":false,"trans":false,"pos":[12,13.5,2,4,13.5,2,4,12.5,2,12,12.5,2,4,13.5,3,12,13.5,3,12,12.5,3,4,12.5,3,4,13.5,2,4,13.5,3,4,12.5,3,4,12.5,2,12,13.5,3,12,13.5,2,12,12.5,2,12,12.5,3,4,13.5,2,12,13.5,2,12,13.5,3,4,13.5,3,4,12.5,3,12,12.5,3,12,12.5,2,4,12.5,2,12,13.5,13,4,13.5,13,4,12.5,13,12,12.5,13,4,13.5,14,12,13.5,14,12,12.5,14,4,12.5,14,4,13.5,13,4,13.5,14,4,12.5,14,4,12.5,13,12,13.5,14,12,13.5,13,12,12.5,13,12,12.5,14,4,13.5,13,12,13.5,13,12,13.5,14,4,13.5,14,4,12.5,14,12,12.5,14,12,12.5,13,4,12.5,13,3,13.5,4,2,13.5,4,2,12.5,4,3,12.5,4,2,13.5,12,3,13.5,12,3,12.5,12,2,12.5,12,2,13.5,4,2,13.5,12,2,12.5,12,2,12.5,4,3,13.5,12,3,13.5,4,3,12.5,4,3,12.5,12,2,13.5,4,3,13.5,4,3,13.5,12,2,13.5,12,2,12.5,12,3,12.5,12,3,12.5,4,2,12.5,4,14,13.5,4,13,13.5,4,13,12.5,4,14,12.5,4,13,13.5,12,14,13.5,12,14,12.5,12,13,12.5,12,13,13.5,4,13,13.5,12,13,12.5,12,13,12.5,4,14,13.5,12,14,13.5,4,14,12.5,4,14,12.5,12,13,13.5,4,14,13.5,4,14,13.5,12,13,13.5,12,13,12.5,12,14,12.5,12,14,12.5,4,13,12.5,4,3.96,13.4,1.84,1.84,13.4,3.96,1.84,12.6,3.96,3.96,12.6,1.84,2.54,13.4,4.66,4.66,13.4,2.54,4.66,12.6,2.54,2.54,12.6,4.66,1.84,13.4,3.96,2.54,13.4,4.66,2.54,12.6,4.66,1.84,12.6,3.96,4.66,13.4,2.54,3.96,13.4,1.84,3.96,12.6,1.84,4.66,12.6,2.54,1.84,13.4,3.96,3.96,13.4,1.84,4.66,13.4,2.54,2.54,13.4,4.66,2.54,12.6,4.66,4.66,12.6,2.54,3.96,12.6,1.84,1.84,12.6,3.96,14.16,13.4,3.96,12.04,13.4,1.84,12.04,12.6,1.84,14.16,12.6,3.96,11.34,13.4,2.54,13.46,13.4,4.66,13.46,12.6,4.66,11.34,12.6,2.54,12.04,13.4,1.84,11.34,13.4,2.54,11.34,12.6,2.54,12.04,12.6,1.84,13.46,13.4,4.66,14.16,13.4,3.96,14.16,12.6,3.96,13.46,12.6,4.66,12.04,13.4,1.84,14.16,13.4,3.96,13.46,13.4,4.66,11.34,13.4,2.54,11.34,12.6,2.54,13.46,12.6,4.66,14.16,12.6,3.96,12.04,12.6,1.84,4.66,13.4,13.46,2.54,13.4,11.34,2.54,12.6,11.34,4.66,12.6,13.46,1.84,13.4,12.04,3.96,13.4,14.16,3.96,12.6,14.16,1.84,12.6,12.04,2.54,13.4,11.34,1.84,13.4,12.04,1.84,12.6,12.04,2.54,12.6,11.34,3.96,13.4,14.16,4.66,13.4,13.46,4.66,12.6,13.46,3.96,12.6,14.16,2.54,13.4,11.34,4.66,13.4,13.46,3.96,13.4,14.16,1.84,13.4,12.04,1.84,12.6,12.04,3.96,12.6,14.16,4.66,12.6,13.46,2.54,12.6,11.34,13.46,13.4,11.34,11.34,13.4,13.46,11.34,12.6,13.46,13.46,12.6,11.34,12.04,13.4,14.16,14.16,13.4,12.04,14.16,12.6,12.04,12.04,12.6,14.16,11.34,13.4,13.46,12.04,13.4,14.16,12.04,12.6,14.16,11.34,12.6,13.46,14.16,13.4,12.04,13.46,13.4,11.34,13.46,12.6,11.34,14.16,12.6,12.04,11.34,13.4,13.46,13.46,13.4,11.34,14.16,13.4,12.04,12.04,13.4,14.16,12.04,12.6,14.16,14.16,12.6,12.04,13.46,12.6,11.34,11.34,12.6,13.46,8.6,13.4,7.4,7.4,13.4,7.4,7.4,12.6,7.4,8.6,12.6,7.4,7.4,13.4,8.6,8.6,13.4,8.6,8.6,12.6,8.6,7.4,12.6,8.6,7.4,13.4,7.4,7.4,13.4,8.6,7.4,12.6,8.6,7.4,12.6,7.4,8.6,13.4,8.6,8.6,13.4,7.4,8.6,12.6,7.4,8.6,12.6,8.6,7.4,13.4,7.4,8.6,13.4,7.4,8.6,13.4,8.6,7.4,13.4,8.6,7.4,12.6,8.6,8.6,12.6,8.6,8.6,12.6,7.4,7.4,12.6,7.4,13,13.2,7.8,3,13.2,7.8,3,12.8,7.8,13,12.8,7.8,3,13.2,8.2,13,13.2,8.2,13,12.8,8.2,3,12.8,8.2,3,13.2,7.8,13,13.2,7.8,13,13.2,8.2,3,13.2,8.2,3,12.8,8.2,13,12.8,8.2,13,12.8,7.8,3,12.8,7.8,7.85,13.15,3,7.85,13.15,13,7.85,12.85,13,7.85,12.85,3,8.15,13.15,13,8.15,13.15,3,8.15,12.85,3,8.15,12.85,13,7.85,13.15,3,8.15,13.15,3,8.15,13.15,13,7.85,13.15,13,7.85,12.85,13,8.15,12.85,13,8.15,12.85,3,7.85,12.85,3],"uv":[0.3125,0.2891,0.4375,0.2891,0.4375,0.3047,0.3125,0.3047,0.3125,0.2891,0.4375,0.2891,0.4375,0.3047,0.3125,0.3047,0.2813,0.2891,0.2969,0.2891,0.2969,0.3047,0.2813,0.3047,0.4531,0.2891,0.4688,0.2891,0.4688,0.3047,0.4531,0.3047,0.8125,0.0313,0.9375,0.0313,0.9375,0.0469,0.8125,0.0469,0.3125,0.4531,0.4375,0.4531,0.4375,0.4688,0.3125,0.4688,0.3125,0.2891,0.4375,0.2891,0.4375,0.3047,0.3125,0.3047,0.3125,0.2891,0.4375,0.2891,0.4375,0.3047,0.3125,0.3047,0.4531,0.2891,0.4688,0.2891,0.4688,0.3047,0.4531,0.3047,0.2813,0.2891,0.2969,0.2891,0.2969,0.3047,0.2813,0.3047,0.8125,0.2031,0.9375,0.2031,0.9375,0.2188,0.8125,0.2188,0.3125,0.2813,0.4375,0.2813,0.4375,0.2969,0.3125,0.2969,0.4531,0.2891,0.4688,0.2891,0.4688,0.3047,0.4531,0.3047,0.2813,0.2891,0.2969,0.2891,0.2969,0.3047,0.2813,0.3047,0.3125,0.2891,0.4375,0.2891,0.4375,0.3047,0.3125,0.3047,0.3125,0.2891,0.4375,0.2891,0.4375,0.3047,0.3125,0.3047,0.7813,0.0625,0.7969,0.0625,0.7969,0.1875,0.7813,0.1875,0.2813,0.3125,0.2969,0.3125,0.2969,0.4375,0.2813,0.4375,0.2813,0.2891,0.2969,0.2891,0.2969,0.3047,0.2813,0.3047,0.4531,0.2891,0.4688,0.2891,0.4688,0.3047,0.4531,0.3047,0.3125,0.2891,0.4375,0.2891,0.4375,0.3047,0.3125,0.3047,0.3125,0.2891,0.4375,0.2891,0.4375,0.3047,0.3125,0.3047,0.9531,0.0625,0.9688,0.0625,0.9688,0.1875,0.9531,0.1875,0.4531,0.3125,0.4688,0.3125,0.4688,0.4375,0.4531,0.4375,0.4258,0.2906,0.4727,0.2906,0.4727,0.3031,0.4258,0.3031,0.2773,0.2906,0.3242,0.2906,0.3242,0.3031,0.2773,0.3031,0.293,0.2906,0.3086,0.2906,0.3086,0.3031,0.293,0.3031,0.4414,0.2906,0.457,0.2906,0.457,0.3031,0.4414,0.3031,0.7773,0.043,0.8242,0.043,0.8242,0.0586,0.7773,0.0586,0.2773,0.4414,0.3242,0.4414,0.3242,0.457,0.2773,0.457,0.2773,0.2906,0.3242,0.2906,0.3242,0.3031,0.2773,0.3031,0.4258,0.2906,0.4727,0.2906,0.4727,0.3031,0.4258,0.3031,0.293,0.2906,0.3086,0.2906,0.3086,0.3031,0.293,0.3031,0.4414,0.2906,0.457,0.2906,0.457,0.3031,0.4414,0.3031,0.9258,0.043,0.9727,0.043,0.9727,0.0586,0.9258,0.0586,0.4258,0.4414,0.4727,0.4414,0.4727,0.457,0.4258,0.457,0.4258,0.2906,0.4727,0.2906,0.4727,0.3031,0.4258,0.3031,0.2773,0.2906,0.3242,0.2906,0.3242,0.3031,0.2773,0.3031,0.4414,0.2906,0.457,0.2906,0.457,0.3031,0.4414,0.3031,0.293,0.2906,0.3086,0.2906,0.3086,0.3031,0.293,0.3031,0.7773,0.1914,0.8242,0.1914,0.8242,0.207,0.7773,0.207,0.2773,0.293,0.3242,0.293,0.3242,0.3086,0.2773,0.3086,0.2773,0.2906,0.3242,0.2906,0.3242,0.3031,0.2773,0.3031,0.4258,0.2906,0.4727,0.2906,0.4727,0.3031,0.4258,0.3031,0.4414,0.2906,0.457,0.2906,0.457,0.3031,0.4414,0.3031,0.293,0.2906,0.3086,0.2906,0.3086,0.3031,0.293,0.3031,0.9258,0.1914,0.9727,0.1914,0.9727,0.207,0.9258,0.207,0.4258,0.293,0.4727,0.293,0.4727,0.3086,0.4258,0.3086,0.1156,0.2906,0.1344,0.2906,0.1344,0.3031,0.1156,0.3031,0.1156,0.2906,0.1344,0.2906,0.1344,0.3031,0.1156,0.3031,0.1156,0.2906,0.1344,0.2906,0.1344,0.3031,0.1156,0.3031,0.1156,0.2906,0.1344,0.2906,0.1344,0.3031,0.1156,0.3031,0.1156,0.3656,0.1344,0.3656,0.1344,0.3844,0.1156,0.3844,0.1156,0.3656,0.1344,0.3656,0.1344,0.3844,0.1156,0.3844,0.0469,0.2938,0.2031,0.2938,0.2031,0.3,0.0469,0.3,0.0469,0.2938,0.2031,0.2938,0.2031,0.3,0.0469,0.3,0.0469,0.3719,0.2031,0.3719,0.2031,0.3781,0.0469,0.3781,0.0469,0.3719,0.2031,0.3719,0.2031,0.3781,0.0469,0.3781,0.0469,0.2945,0.2031,0.2945,0.2031,0.2992,0.0469,0.2992,0.0469,0.2945,0.2031,0.2945,0.2031,0.2992,0.0469,0.2992,0.1227,0.2969,0.1273,0.2969,0.1273,0.4531,0.1227,0.4531,0.1227,0.2969,0.1273,0.2969,0.1273,0.4531,0.1227,0.4531],"shade":[0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.7,0.7,0.7,0.7,1,0.5,0.7,0.7,0.7,0.7,1,0.5,0.7,0.7,0.7,0.7,1,0.5,0.7,0.7,0.7,0.7,1,0.5,0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,1,0.5,0.6,0.6,1,0.5],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22,24,26,25,24,27,26,28,30,29,28,31,30,32,34,33,32,35,34,36,38,37,36,39,38,40,42,41,40,43,42,44,46,45,44,47,46,48,50,49,48,51,50,52,54,53,52,55,54,56,58,57,56,59,58,60,62,61,60,63,62,64,66,65,64,67,66,68,70,69,68,71,70,72,74,73,72,75,74,76,78,77,76,79,78,80,82,81,80,83,82,84,86,85,84,87,86,88,90,89,88,91,90,92,94,93,92,95,94,96,98,97,96,99,98,100,102,101,100,103,102,104,106,105,104,107,106,108,110,109,108,111,110,112,114,113,112,115,114,116,118,117,116,119,118,120,122,121,120,123,122,124,126,125,124,127,126,128,130,129,128,131,130,132,134,133,132,135,134,136,138,137,136,139,138,140,142,141,140,143,142,144,146,145,144,147,146,148,150,149,148,151,150,152,154,153,152,155,154,156,158,157,156,159,158,160,162,161,160,163,162,164,166,165,164,167,166,168,170,169,168,171,170,172,174,173,172,175,174,176,178,177,176,179,178,180,182,181,180,183,182,184,186,185,184,187,186,188,190,189,188,191,190,192,194,193,192,195,194,196,198,197,196,199,198,200,202,201,200,203,202,204,206,205,204,207,206,208,210,209,208,211,210,212,214,213,212,215,214,216,218,217,216,219,218,220,222,221,220,223,222,224,226,225,224,227,226,228,230,229,228,231,230,232,234,233,232,235,234,236,238,237,236,239,238,240,242,241,240,243,242,244,246,245,244,247,246]},{"tex":"cccloud:block/relay_advanced","emit":true,"trans":false,"pos":[8.5,13.4,1.7,7.5,13.4,1.7,7.5,12.6,1.7,8.5,12.6,1.7,7.5,13.4,1.7,7.5,13.4,2,7.5,12.6,2,7.5,12.6,1.7,8.5,13.4,2,8.5,13.4,1.7,8.5,12.6,1.7,8.5,12.6,2,7.5,13.4,1.7,8.5,13.4,1.7,8.5,13.4,2,7.5,13.4,2,7.5,12.6,2,8.5,12.6,2,8.5,12.6,1.7,7.5,12.6,1.7,7.5,13.4,14.3,8.5,13.4,14.3,8.5,12.6,14.3,7.5,12.6,14.3,7.5,13.4,14,7.5,13.4,14.3,7.5,12.6,14.3,7.5,12.6,14,8.5,13.4,14.3,8.5,13.4,14,8.5,12.6,14,8.5,12.6,14.3,7.5,13.4,14,8.5,13.4,14,8.5,13.4,14.3,7.5,13.4,14.3,7.5,12.6,14.3,8.5,12.6,14.3,8.5,12.6,14,7.5,12.6,14,2,13.4,7.5,1.7,13.4,7.5,1.7,12.6,7.5,2,12.6,7.5,1.7,13.4,8.5,2,13.4,8.5,2,12.6,8.5,1.7,12.6,8.5,1.7,13.4,7.5,1.7,13.4,8.5,1.7,12.6,8.5,1.7,12.6,7.5,1.7,13.4,7.5,2,13.4,7.5,2,13.4,8.5,1.7,13.4,8.5,1.7,12.6,8.5,2,12.6,8.5,2,12.6,7.5,1.7,12.6,7.5,14.3,13.4,7.5,14,13.4,7.5,14,12.6,7.5,14.3,12.6,7.5,14,13.4,8.5,14.3,13.4,8.5,14.3,12.6,8.5,14,12.6,8.5,14.3,13.4,8.5,14.3,13.4,7.5,14.3,12.6,7.5,14.3,12.6,8.5,14,13.4,7.5,14.3,13.4,7.5,14.3,13.4,8.5,14,13.4,8.5,14,12.6,8.5,14.3,12.6,8.5,14.3,12.6,7.5,14,12.6,7.5],"uv":[0.6172,0.2906,0.6328,0.2906,0.6328,0.3031,0.6172,0.3031,0.5266,0.2906,0.5313,0.2906,0.5313,0.3031,0.5266,0.3031,0.7188,0.2906,0.7234,0.2906,0.7234,0.3031,0.7188,0.3031,0.6172,0.2766,0.6328,0.2766,0.6328,0.2813,0.6172,0.2813,0.6172,0.4688,0.6328,0.4688,0.6328,0.4734,0.6172,0.4734,0.6172,0.2906,0.6328,0.2906,0.6328,0.3031,0.6172,0.3031,0.7188,0.2906,0.7234,0.2906,0.7234,0.3031,0.7188,0.3031,0.5266,0.2906,0.5313,0.2906,0.5313,0.3031,0.5266,0.3031,0.6172,0.4688,0.6328,0.4688,0.6328,0.4734,0.6172,0.4734,0.6172,0.2766,0.6328,0.2766,0.6328,0.2813,0.6172,0.2813,0.7188,0.2906,0.7234,0.2906,0.7234,0.3031,0.7188,0.3031,0.5266,0.2906,0.5313,0.2906,0.5313,0.3031,0.5266,0.3031,0.6172,0.2906,0.6328,0.2906,0.6328,0.3031,0.6172,0.3031,0.5266,0.3672,0.5313,0.3672,0.5313,0.3828,0.5266,0.3828,0.5266,0.3672,0.5313,0.3672,0.5313,0.3828,0.5266,0.3828,0.5266,0.2906,0.5313,0.2906,0.5313,0.3031,0.5266,0.3031,0.7188,0.2906,0.7234,0.2906,0.7234,0.3031,0.7188,0.3031,0.6172,0.2906,0.6328,0.2906,0.6328,0.3031,0.6172,0.3031,0.7188,0.3672,0.7234,0.3672,0.7234,0.3828,0.7188,0.3828,0.7188,0.3672,0.7234,0.3672,0.7234,0.3828,0.7188,0.3828],"shade":[1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22,24,26,25,24,27,26,28,30,29,28,31,30,32,34,33,32,35,34,36,38,37,36,39,38,40,42,41,40,43,42,44,46,45,44,47,46,48,50,49,48,51,50,52,54,53,52,55,54,56,58,57,56,59,58,60,62,61,60,63,62,64,66,65,64,67,66,68,70,69,68,71,70,72,74,73,72,75,74,76,78,77,76,79,78]}],"tall":1,"status":{"online":{},"connecting":{"cccloud:block/relay_status_online":"cccloud:block/relay_status_connecting"},"offline":{"cccloud:block/relay_status_online":"cccloud:block/relay_status_offline"},"no_power":{"cccloud:block/relay_status_online":"cccloud:block/relay_status_no_power"}}},"ender_relay":{"parts":[{"tex":"cccloud:block/relay_ender","emit":false,"trans":false,"pos":[14,6,2,2,6,2,2,0,2,14,0,2,2,6,14,14,6,14,14,0,14,2,0,14,2,6,2,2,6,14,2,0,14,2,0,2,14,6,14,14,6,2,14,0,2,14,0,14,2,6,2,14,6,2,14,6,14,2,6,14,2,0,14,14,0,14,14,0,2,2,0,2,12,7,4,4,7,4,4,6,4,12,6,4,4,7,12,12,7,12,12,6,12,4,6,12,4,7,4,4,7,12,4,6,12,4,6,4,12,7,12,12,7,4,12,6,4,12,6,12,4,7,4,12,7,4,12,7,12,4,7,12,8.4,11.47,2.22,7.6,11.47,2.22,7.6,6.85,4.13,8.4,6.85,4.13,7.6,11.77,2.96,8.4,11.77,2.96,8.4,7.15,4.87,7.6,7.15,4.87,7.6,11.47,2.22,7.6,11.77,2.96,7.6,7.15,4.87,7.6,6.85,4.13,8.4,11.77,2.96,8.4,11.47,2.22,8.4,6.85,4.13,8.4,7.15,4.87,7.6,11.47,2.22,8.4,11.47,2.22,8.4,11.77,2.96,7.6,11.77,2.96,8.4,11.77,13.04,7.6,11.77,13.04,7.6,7.15,11.13,8.4,7.15,11.13,7.6,11.47,13.78,8.4,11.47,13.78,8.4,6.85,11.87,7.6,6.85,11.87,7.6,11.77,13.04,7.6,11.47,13.78,7.6,6.85,11.87,7.6,7.15,11.13,8.4,11.47,13.78,8.4,11.77,13.04,8.4,7.15,11.13,8.4,6.85,11.87,7.6,11.77,13.04,8.4,11.77,13.04,8.4,11.47,13.78,7.6,11.47,13.78,2.96,11.77,7.6,2.22,11.47,7.6,4.13,6.85,7.6,4.87,7.15,7.6,2.22,11.47,8.4,2.96,11.77,8.4,4.87,7.15,8.4,4.13,6.85,8.4,2.22,11.47,7.6,2.22,11.47,8.4,4.13,6.85,8.4,4.13,6.85,7.6,2.96,11.77,8.4,2.96,11.77,7.6,4.87,7.15,7.6,4.87,7.15,8.4,2.22,11.47,7.6,2.96,11.77,7.6,2.96,11.77,8.4,2.22,11.47,8.4,13.78,11.47,7.6,13.04,11.77,7.6,11.13,7.15,7.6,11.87,6.85,7.6,13.04,11.77,8.4,13.78,11.47,8.4,11.87,6.85,8.4,11.13,7.15,8.4,13.04,11.77,7.6,13.04,11.77,8.4,11.13,7.15,8.4,11.13,7.15,7.6,13.78,11.47,8.4,13.78,11.47,7.6,11.87,6.85,7.6,11.87,6.85,8.4,13.04,11.77,7.6,13.78,11.47,7.6,13.78,11.47,8.4,13.04,11.77,8.4],"uv":[0.0313,0.1563,0.2188,0.1563,0.2188,0.25,0.0313,0.25,0.0313,0.1563,0.2188,0.1563,0.2188,0.25,0.0313,0.25,0.0313,0.1563,0.2188,0.1563,0.2188,0.25,0.0313,0.25,0.0313,0.1563,0.2188,0.1563,0.2188,0.25,0.0313,0.25,0.2813,0.0313,0.4688,0.0313,0.4688,0.2188,0.2813,0.2188,0.0313,0.2813,0.2188,0.2813,0.2188,0.4688,0.0313,0.4688,0.8125,0.1406,0.9375,0.1406,0.9375,0.1563,0.8125,0.1563,0.8125,0.1406,0.9375,0.1406,0.9375,0.1563,0.8125,0.1563,0.8125,0.1406,0.9375,0.1406,0.9375,0.1563,0.8125,0.1563,0.8125,0.1406,0.9375,0.1406,0.9375,0.1563,0.8125,0.1563,0.3125,0.0625,0.4375,0.0625,0.4375,0.1875,0.3125,0.1875,0.3688,0.3125,0.3813,0.3125,0.3813,0.3906,0.3688,0.3906,0.3688,0.3125,0.3813,0.3125,0.3813,0.3906,0.3688,0.3906,0.3141,0.3125,0.3266,0.3125,0.3266,0.3906,0.3141,0.3906,0.4234,0.3125,0.4359,0.3125,0.4359,0.3906,0.4234,0.3906,0.3688,0.3141,0.3813,0.3141,0.3813,0.3266,0.3688,0.3266,0.3688,0.3125,0.3813,0.3125,0.3813,0.3906,0.3688,0.3906,0.3688,0.3125,0.3813,0.3125,0.3813,0.3906,0.3688,0.3906,0.4234,0.3125,0.4359,0.3125,0.4359,0.3906,0.4234,0.3906,0.3141,0.3125,0.3266,0.3125,0.3266,0.3906,0.3141,0.3906,0.3688,0.4234,0.3813,0.4234,0.3813,0.4359,0.3688,0.4359,0.4234,0.3125,0.4359,0.3125,0.4359,0.3906,0.4234,0.3906,0.3141,0.3125,0.3266,0.3125,0.3266,0.3906,0.3141,0.3906,0.3688,0.3125,0.3813,0.3125,0.3813,0.3906,0.3688,0.3906,0.3688,0.3125,0.3813,0.3125,0.3813,0.3906,0.3688,0.3906,0.3141,0.3688,0.3266,0.3688,0.3266,0.3813,0.3141,0.3813,0.3141,0.3125,0.3266,0.3125,0.3266,0.3906,0.3141,0.3906,0.4234,0.3125,0.4359,0.3125,0.4359,0.3906,0.4234,0.3906,0.3688,0.3125,0.3813,0.3125,0.3813,0.3906,0.3688,0.3906,0.3688,0.3125,0.3813,0.3125,0.3813,0.3906,0.3688,0.3906,0.4234,0.3688,0.4359,0.3688,0.4359,0.3813,0.4234,0.3813],"shade":[0.8,0.8,0.6,0.6,1,0.5,0.8,0.8,0.6,0.6,1,0.76,0.83,0.6,0.6,0.97,0.83,0.76,0.6,0.6,0.97,0.8,0.8,0.59,0.66,0.94,0.8,0.8,0.66,0.59,0.94],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22,24,26,25,24,27,26,28,30,29,28,31,30,32,34,33,32,35,34,36,38,37,36,39,38,40,42,41,40,43,42,44,46,45,44,47,46,48,50,49,48,51,50,52,54,53,52,55,54,56,58,57,56,59,58,60,62,61,60,63,62,64,66,65,64,67,66,68,70,69,68,71,70,72,74,73,72,75,74,76,78,77,76,79,78,80,82,81,80,83,82,84,86,85,84,87,86,88,90,89,88,91,90,92,94,93,92,95,94,96,98,97,96,99,98,100,102,101,100,103,102,104,106,105,104,107,106,108,110,109,108,111,110,112,114,113,112,115,114,116,118,117,116,119,118,120,122,121,120,123,122]},{"tex":"cccloud:block/relay_status_online","emit":true,"trans":false,"pos":[11,5,1.9,5,5,1.9,5,4,1.9,11,4,1.9,5,5,1.9,5,5,2,5,4,2,5,4,1.9,11,5,2,11,5,1.9,11,4,1.9,11,4,2,5,5,1.9,11,5,1.9,11,5,2,5,5,2,5,4,2,11,4,2,11,4,1.9,5,4,1.9,5,5,14.1,11,5,14.1,11,4,14.1,5,4,14.1,5,5,14,5,5,14.1,5,4,14.1,5,4,14,11,5,14.1,11,5,14,11,4,14,11,4,14.1,5,5,14,11,5,14,11,5,14.1,5,5,14.1,5,4,14.1,11,4,14.1,11,4,14,5,4,14,2,5,5,1.9,5,5,1.9,4,5,2,4,5,1.9,5,11,2,5,11,2,4,11,1.9,4,11,1.9,5,5,1.9,5,11,1.9,4,11,1.9,4,5,1.9,5,5,2,5,5,2,5,11,1.9,5,11,1.9,4,11,2,4,11,2,4,5,1.9,4,5,14.1,5,5,14,5,5,14,4,5,14.1,4,5,14,5,11,14.1,5,11,14.1,4,11,14,4,11,14.1,5,11,14.1,5,5,14.1,4,5,14.1,4,11,14,5,5,14.1,5,5,14.1,5,11,14,5,11,14,4,11,14.1,4,11,14.1,4,5,14,4,5],"uv":[0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625,0,0,0.375,0,0.375,0.0625,0,0.0625],"shade":[1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22,24,26,25,24,27,26,28,30,29,28,31,30,32,34,33,32,35,34,36,38,37,36,39,38,40,42,41,40,43,42,44,46,45,44,47,46,48,50,49,48,51,50,52,54,53,52,55,54,56,58,57,56,59,58,60,62,61,60,63,62,64,66,65,64,67,66,68,70,69,68,71,70,72,74,73,72,75,74,76,78,77,76,79,78]},{"tex":"cccloud:block/relay_ender","emit":true,"trans":false,"pos":[8.5,12.17,1.82,7.5,12.17,1.82,7.5,11.43,2.12,8.5,11.43,2.12,7.5,12.55,2.74,8.5,12.55,2.74,8.5,11.81,3.05,7.5,11.81,3.05,7.5,12.17,1.82,7.5,12.55,2.74,7.5,11.81,3.05,7.5,11.43,2.12,8.5,12.55,2.74,8.5,12.17,1.82,8.5,11.43,2.12,8.5,11.81,3.05,7.5,12.17,1.82,8.5,12.17,1.82,8.5,12.55,2.74,7.5,12.55,2.74,7.5,11.81,3.05,8.5,11.81,3.05,8.5,11.43,2.12,7.5,11.43,2.12,8.5,12.55,13.26,7.5,12.55,13.26,7.5,11.81,12.95,8.5,11.81,12.95,7.5,12.17,14.18,8.5,12.17,14.18,8.5,11.43,13.88,7.5,11.43,13.88,7.5,12.55,13.26,7.5,12.17,14.18,7.5,11.43,13.88,7.5,11.81,12.95,8.5,12.17,14.18,8.5,12.55,13.26,8.5,11.81,12.95,8.5,11.43,13.88,7.5,12.55,13.26,8.5,12.55,13.26,8.5,12.17,14.18,7.5,12.17,14.18,7.5,11.43,13.88,8.5,11.43,13.88,8.5,11.81,12.95,7.5,11.81,12.95,2.74,12.55,7.5,1.82,12.17,7.5,2.12,11.43,7.5,3.05,11.81,7.5,1.82,12.17,8.5,2.74,12.55,8.5,3.05,11.81,8.5,2.12,11.43,8.5,1.82,12.17,7.5,1.82,12.17,8.5,2.12,11.43,8.5,2.12,11.43,7.5,2.74,12.55,8.5,2.74,12.55,7.5,3.05,11.81,7.5,3.05,11.81,8.5,1.82,12.17,7.5,2.74,12.55,7.5,2.74,12.55,8.5,1.82,12.17,8.5,2.12,11.43,8.5,3.05,11.81,8.5,3.05,11.81,7.5,2.12,11.43,7.5,14.18,12.17,7.5,13.26,12.55,7.5,12.95,11.81,7.5,13.88,11.43,7.5,13.26,12.55,8.5,14.18,12.17,8.5,13.88,11.43,8.5,12.95,11.81,8.5,13.26,12.55,7.5,13.26,12.55,8.5,12.95,11.81,8.5,12.95,11.81,7.5,14.18,12.17,8.5,14.18,12.17,7.5,13.88,11.43,7.5,13.88,11.43,8.5,13.26,12.55,7.5,14.18,12.17,7.5,14.18,12.17,8.5,13.26,12.55,8.5,12.95,11.81,8.5,13.88,11.43,8.5,13.88,11.43,7.5,12.95,11.81,7.5],"uv":[0.6172,0.3,0.6328,0.3,0.6328,0.3125,0.6172,0.3125,0.6172,0.3,0.6328,0.3,0.6328,0.3125,0.6172,0.3125,0.5625,0.3,0.5781,0.3,0.5781,0.3125,0.5625,0.3125,0.6719,0.3,0.6875,0.3,0.6875,0.3125,0.6719,0.3125,0.6172,0.3125,0.6328,0.3125,0.6328,0.3281,0.6172,0.3281,0.6172,0.4219,0.6328,0.4219,0.6328,0.4375,0.6172,0.4375,0.6172,0.3,0.6328,0.3,0.6328,0.3125,0.6172,0.3125,0.6172,0.3,0.6328,0.3,0.6328,0.3125,0.6172,0.3125,0.6719,0.3,0.6875,0.3,0.6875,0.3125,0.6719,0.3125,0.5625,0.3,0.5781,0.3,0.5781,0.3125,0.5625,0.3125,0.6172,0.4219,0.6328,0.4219,0.6328,0.4375,0.6172,0.4375,0.6172,0.3125,0.6328,0.3125,0.6328,0.3281,0.6172,0.3281,0.6719,0.3,0.6875,0.3,0.6875,0.3125,0.6719,0.3125,0.5625,0.3,0.5781,0.3,0.5781,0.3125,0.5625,0.3125,0.6172,0.3,0.6328,0.3,0.6328,0.3125,0.6172,0.3125,0.6172,0.3,0.6328,0.3,0.6328,0.3125,0.6172,0.3125,0.5625,0.3672,0.5781,0.3672,0.5781,0.3828,0.5625,0.3828,0.5625,0.3672,0.5781,0.3672,0.5781,0.3828,0.5625,0.3828,0.5625,0.3,0.5781,0.3,0.5781,0.3125,0.5625,0.3125,0.6719,0.3,0.6875,0.3,0.6875,0.3125,0.6719,0.3125,0.6172,0.3,0.6328,0.3,0.6328,0.3125,0.6172,0.3125,0.6172,0.3,0.6328,0.3,0.6328,0.3125,0.6172,0.3125,0.6719,0.3672,0.6875,0.3672,0.6875,0.3828,0.6719,0.3828,0.6719,0.3672,0.6875,0.3672,0.6875,0.3828,0.6719,0.3828],"shade":[1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22,24,26,25,24,27,26,28,30,29,28,31,30,32,34,33,32,35,34,36,38,37,36,39,38,40,42,41,40,43,42,44,46,45,44,47,46,48,50,49,48,51,50,52,54,53,52,55,54,56,58,57,56,59,58,60,62,61,60,63,62,64,66,65,64,67,66,68,70,69,68,71,70,72,74,73,72,75,74,76,78,77,76,79,78,80,82,81,80,83,82,84,86,85,84,87,86,88,90,89,88,91,90,92,94,93,92,95,94]},{"tex":"cccloud:block/relay_ender","emit":true,"trans":false,"pos":[10,14.33,8,6,14.33,8,6,11.5,5.17,10,11.5,5.17,6,11.5,10.83,10,11.5,10.83,10,8.67,8,6,8.67,8,6,14.33,8,6,11.5,10.83,6,8.67,8,6,11.5,5.17,10,11.5,10.83,10,14.33,8,10,11.5,5.17,10,8.67,8,6,14.33,8,10,14.33,8,10,11.5,10.83,6,11.5,10.83,6,8.67,8,10,8.67,8,10,11.5,5.17,6,11.5,5.17,8,13.5,5.17,5.17,13.5,8,5.17,9.5,8,8,9.5,5.17,8,13.5,10.83,10.83,13.5,8,10.83,9.5,8,8,9.5,10.83,5.17,13.5,8,8,13.5,10.83,8,9.5,10.83,5.17,9.5,8,10.83,13.5,8,8,13.5,5.17,8,9.5,5.17,10.83,9.5,8,5.17,13.5,8,8,13.5,5.17,10.83,13.5,8,8,13.5,10.83,8,9.5,10.83,10.83,9.5,8,8,9.5,5.17,5.17,9.5,8,8,14.33,6,5.17,11.5,6,8,8.67,6,10.83,11.5,6,5.17,11.5,10,8,14.33,10,10.83,11.5,10,8,8.67,10,5.17,11.5,6,5.17,11.5,10,8,8.67,10,8,8.67,6,8,14.33,10,8,14.33,6,10.83,11.5,6,10.83,11.5,10,5.17,11.5,6,8,14.33,6,8,14.33,10,5.17,11.5,10,8,8.67,10,10.83,11.5,10,10.83,11.5,6,8,8.67,6,9.25,12.75,6.75,6.75,12.75,6.75,6.75,10.25,6.75,9.25,10.25,6.75,6.75,12.75,9.25,9.25,12.75,9.25,9.25,10.25,9.25,6.75,10.25,9.25,6.75,12.75,6.75,6.75,12.75,9.25,6.75,10.25,9.25,6.75,10.25,6.75,9.25,12.75,9.25,9.25,12.75,6.75,9.25,10.25,6.75,9.25,10.25,9.25,6.75,12.75,6.75,9.25,12.75,6.75,9.25,12.75,9.25,6.75,12.75,9.25,6.75,10.25,9.25,9.25,10.25,9.25,9.25,10.25,6.75,6.75,10.25,6.75],"uv":[0,0.5,0.0625,0.5,0.0625,0.5625,0,0.5625,0,0.5,0.0625,0.5,0.0625,0.5625,0,0.5625,0,0.5,0.0625,0.5,0.0625,0.5625,0,0.5625,0,0.5,0.0625,0.5,0.0625,0.5625,0,0.5625,0,0.5,0.0625,0.5,0.0625,0.5625,0,0.5625,0,0.5,0.0625,0.5,0.0625,0.5625,0,0.5625,0,0.5,0.0625,0.5,0.0625,0.5625,0,0.5625,0,0.5,0.0625,0.5,0.0625,0.5625,0,0.5625,0,0.5,0.0625,0.5,0.0625,0.5625,0,0.5625,0,0.5,0.0625,0.5,0.0625,0.5625,0,0.5625,0,0.5,0.0625,0.5,0.0625,0.5625,0,0.5625,0,0.5,0.0625,0.5,0.0625,0.5625,0,0.5625,0,0.5,0.0625,0.5,0.0625,0.5625,0,0.5625,0,0.5,0.0625,0.5,0.0625,0.5625,0,0.5625,0,0.5,0.0625,0.5,0.0625,0.5625,0,0.5625,0,0.5,0.0625,0.5,0.0625,0.5625,0,0.5625,0,0.5,0.0625,0.5,0.0625,0.5625,0,0.5625,0,0.5,0.0625,0.5,0.0625,0.5625,0,0.5625,0.6055,0.3008,0.6445,0.3008,0.6445,0.3398,0.6055,0.3398,0.6055,0.3008,0.6445,0.3008,0.6445,0.3398,0.6055,0.3398,0.6055,0.3008,0.6445,0.3008,0.6445,0.3398,0.6055,0.3398,0.6055,0.3008,0.6445,0.3008,0.6445,0.3398,0.6055,0.3398,0.6055,0.3555,0.6445,0.3555,0.6445,0.3945,0.6055,0.3945,0.6055,0.3555,0.6445,0.3555,0.6445,0.3945,0.6055,0.3945],"shade":[1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22,24,26,25,24,27,26,28,30,29,28,31,30,32,34,33,32,35,34,36,38,37,36,39,38,40,42,41,40,43,42,44,46,45,44,47,46,48,50,49,48,51,50,52,54,53,52,55,54,56,58,57,56,59,58,60,62,61,60,63,62,64,66,65,64,67,66,68,70,69,68,71,70,72,74,73,72,75,74,76,78,77,76,79,78,80,82,81,80,83,82,84,86,85,84,87,86,88,90,89,88,91,90,92,94,93,92,95,94]}],"tall":1,"status":{"online":{},"connecting":{"cccloud:block/relay_status_online":"cccloud:block/relay_status_connecting"},"offline":{"cccloud:block/relay_status_online":"cccloud:block/relay_status_offline"},"no_power":{"cccloud:block/relay_status_online":"cccloud:block/relay_status_no_power"}}},"uplink":{"parts":[{"tex":"cccloud:block/uplink","emit":false,"trans":false,"pos":[15,0,1,1,0,1,1,0,15,15,0,15,1,1,1,15,1,1,15,1,15,1,1,15,1,0,1,1,1,1,1,1,15,1,0,15,15,1,1,15,0,1,15,0,15,15,1,15,1,0,1,15,0,1,15,1,1,1,1,1,1,1,15,15,1,15,15,0,15,1,0,15,1,2,11,15,2,11,15,2,15,1,2,15,1,1,11,1,2,11,1,2,15,1,1,15,15,2,11,15,1,11,15,1,15,15,2,15,1,1,11,15,1,11,15,2,11,1,2,11,1,2,15,15,2,15,15,1,15,1,1,15,1,2,1,15,2,1,15,2,5,1,2,5,1,1,1,1,2,1,1,2,5,1,1,5,15,2,1,15,1,1,15,1,5,15,2,5,1,1,1,15,1,1,15,2,1,1,2,1,1,2,5,15,2,5,15,1,5,1,1,5,1,2,5,5,2,5,5,2,11,1,2,11,1,1,5,1,2,5,1,2,11,1,1,11,5,2,5,5,1,5,5,1,11,5,2,11,11,2,5,15,2,5,15,2,11,11,2,11,11,1,5,11,2,5,11,2,11,11,1,11,15,2,5,15,1,5,15,1,11,15,2,11,5,2,5,6,2,5,6,2,6,5,2,6,5,1,5,5,2,5,5,2,6,5,1,6,6,2,5,6,1,5,6,1,6,6,2,6,5,1,5,6,1,5,6,2,5,5,2,5,5,2,6,6,2,6,6,1,6,5,1,6,10,2,5,11,2,5,11,2,6,10,2,6,10,1,5,10,2,5,10,2,6,10,1,6,11,2,5,11,1,5,11,1,6,11,2,6,10,1,5,11,1,5,11,2,5,10,2,5,10,2,6,11,2,6,11,1,6,10,1,6,5,2,10,6,2,10,6,2,11,5,2,11,5,1,10,5,2,10,5,2,11,5,1,11,6,2,10,6,1,10,6,1,11,6,2,11,5,1,10,6,1,10,6,2,10,5,2,10,5,2,11,6,2,11,6,1,11,5,1,11,10,2,10,11,2,10,11,2,11,10,2,11,10,1,10,10,2,10,10,2,11,10,1,11,11,2,10,11,1,10,11,1,11,11,2,11,10,1,10,11,1,10,11,2,10,10,2,10,10,2,11,11,2,11,11,1,11,10,1,11],"uv":[0.0156,0.0156,0.2344,0.0156,0.2344,0.2344,0.0156,0.2344,0.0156,0.0156,0.2344,0.0156,0.2344,0.2344,0.0156,0.2344,0.5,0.0156,0.5156,0.0156,0.5156,0.2344,0.5,0.2344,0.7344,0.0156,0.75,0.0156,0.75,0.2344,0.7344,0.2344,0.5156,0,0.7344,0,0.7344,0.0156,0.5156,0.0156,0.5156,0.2344,0.7344,0.2344,0.7344,0.25,0.5156,0.25,0.0156,0.1719,0.2344,0.1719,0.2344,0.2344,0.0156,0.2344,0.5156,0.1719,0.5313,0.1719,0.5313,0.2344,0.5156,0.2344,0.7188,0.1719,0.7344,0.1719,0.7344,0.2344,0.7188,0.2344,0.0156,0.2656,0.2344,0.2656,0.2344,0.2813,0.0156,0.2813,0.5156,0.2188,0.7344,0.2188,0.7344,0.2344,0.5156,0.2344,0.0156,0.0156,0.2344,0.0156,0.2344,0.0781,0.0156,0.0781,0.5156,0.0156,0.5313,0.0156,0.5313,0.0781,0.5156,0.0781,0.7188,0.0156,0.7344,0.0156,0.7344,0.0781,0.7188,0.0781,0.5156,0.0156,0.7344,0.0156,0.7344,0.0313,0.5156,0.0313,0.0156,0.4688,0.2344,0.4688,0.2344,0.4844,0.0156,0.4844,0.0156,0.0781,0.0781,0.0781,0.0781,0.1719,0.0156,0.1719,0.5156,0.0781,0.5313,0.0781,0.5313,0.1719,0.5156,0.1719,0.2188,0.3281,0.2344,0.3281,0.2344,0.4219,0.2188,0.4219,0.1719,0.0781,0.2344,0.0781,0.2344,0.1719,0.1719,0.1719,0.0156,0.3281,0.0313,0.3281,0.0313,0.4219,0.0156,0.4219,0.7188,0.0781,0.7344,0.0781,0.7344,0.1719,0.7188,0.1719,0.0781,0.0781,0.0938,0.0781,0.0938,0.0938,0.0781,0.0938,0.0156,0.3281,0.0313,0.3281,0.0313,0.3438,0.0156,0.3438,0.2188,0.3281,0.2344,0.3281,0.2344,0.3438,0.2188,0.3438,0.0781,0.2656,0.0938,0.2656,0.0938,0.2813,0.0781,0.2813,0.0781,0.4688,0.0938,0.4688,0.0938,0.4844,0.0781,0.4844,0.1563,0.0781,0.1719,0.0781,0.1719,0.0938,0.1563,0.0938,0.0156,0.3281,0.0313,0.3281,0.0313,0.3438,0.0156,0.3438,0.2188,0.3281,0.2344,0.3281,0.2344,0.3438,0.2188,0.3438,0.1563,0.2656,0.1719,0.2656,0.1719,0.2813,0.1563,0.2813,0.1563,0.4688,0.1719,0.4688,0.1719,0.4844,0.1563,0.4844,0.0781,0.1563,0.0938,0.1563,0.0938,0.1719,0.0781,0.1719,0.0156,0.4063,0.0313,0.4063,0.0313,0.4219,0.0156,0.4219,0.2188,0.4063,0.2344,0.4063,0.2344,0.4219,0.2188,0.4219,0.0781,0.2656,0.0938,0.2656,0.0938,0.2813,0.0781,0.2813,0.0781,0.4688,0.0938,0.4688,0.0938,0.4844,0.0781,0.4844,0.1563,0.1563,0.1719,0.1563,0.1719,0.1719,0.1563,0.1719,0.0156,0.4063,0.0313,0.4063,0.0313,0.4219,0.0156,0.4219,0.2188,0.4063,0.2344,0.4063,0.2344,0.4219,0.2188,0.4219,0.1563,0.2656,0.1719,0.2656,0.1719,0.2813,0.1563,0.2813,0.1563,0.4688,0.1719,0.4688,0.1719,0.4844,0.1563,0.4844],"shade":[0.5,1,0.6,0.6,0.8,0.8,1,0.6,0.6,0.8,0.8,1,0.6,0.6,0.8,0.8,1,0.6,0.6,1,0.6,0.6,1,0.6,0.6,0.8,0.8,1,0.6,0.6,0.8,0.8,1,0.6,0.6,0.8,0.8,1,0.6,0.6,0.8,0.8],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22,24,26,25,24,27,26,28,30,29,28,31,30,32,34,33,32,35,34,36,38,37,36,39,38,40,42,41,40,43,42,44,46,45,44,47,46,48,50,49,48,51,50,52,54,53,52,55,54,56,58,57,56,59,58,60,62,61,60,63,62,64,66,65,64,67,66,68,70,69,68,71,70,72,74,73,72,75,74,76,78,77,76,79,78,80,82,81,80,83,82,84,86,85,84,87,86,88,90,89,88,91,90,92,94,93,92,95,94,96,98,97,96,99,98,100,102,101,100,103,102,104,106,105,104,107,106,108,110,109,108,111,110,112,114,113,112,115,114,116,118,117,116,119,118,120,122,121,120,123,122,124,126,125,124,127,126,128,130,129,128,131,130,132,134,133,132,135,134,136,138,137,136,139,138,140,142,141,140,143,142,144,146,145,144,147,146,148,150,149,148,151,150,152,154,153,152,155,154,156,158,157,156,159,158,160,162,161,160,163,162,164,166,165,164,167,166]},{"tex":"cccloud:block/uplink_status_online","emit":true,"trans":false,"pos":[4,2.25,13,12,2.25,13,12,2.25,14,4,2.25,14,4,2,13,4,2.25,13,4,2.25,14,4,2,14,12,2.25,13,12,2,13,12,2,14,12,2.25,14,4,2,13,12,2,13,12,2.25,13,4,2.25,13,4,2.25,14,12,2.25,14,12,2,14,4,2,14],"uv":[0,0.5,0.5,0.5,0.5,0.5625,0,0.5625,0,0.5,0.5,0.5,0.5,0.5625,0,0.5625,0,0.5,0.5,0.5,0.5,0.5625,0,0.5625,0,0.5,0.5,0.5,0.5,0.5625,0,0.5625,0,0.5,0.5,0.5,0.5,0.5625,0,0.5625],"shade":[1,1,1,1,1],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18]}],"tall":1,"status":{"online":{},"connecting":{"cccloud:block/uplink_status_online":"cccloud:block/uplink_status_connecting"},"offline":{"cccloud:block/uplink_status_online":"cccloud:block/uplink_status_offline"}}},"advanced_uplink":{"parts":[{"tex":"cccloud:block/uplink_advanced","emit":false,"trans":false,"pos":[15,0,1,1,0,1,1,0,15,15,0,15,1,1,1,15,1,1,15,1,15,1,1,15,1,0,1,1,1,1,1,1,15,1,0,15,15,1,1,15,0,1,15,0,15,15,1,15,1,0,1,15,0,1,15,1,1,1,1,1,1,1,15,15,1,15,15,0,15,1,0,15,1,2,11,15,2,11,15,2,15,1,2,15,1,1,11,1,2,11,1,2,15,1,1,15,15,2,11,15,1,11,15,1,15,15,2,15,1,1,11,15,1,11,15,2,11,1,2,11,1,2,15,15,2,15,15,1,15,1,1,15,1,2,1,15,2,1,15,2,5,1,2,5,1,1,1,1,2,1,1,2,5,1,1,5,15,2,1,15,1,1,15,1,5,15,2,5,1,1,1,15,1,1,15,2,1,1,2,1,1,2,5,15,2,5,15,1,5,1,1,5,1,2,5,5,2,5,5,2,11,1,2,11,1,1,5,1,2,5,1,2,11,1,1,11,5,2,5,5,1,5,5,1,11,5,2,11,11,2,5,15,2,5,15,2,11,11,2,11,11,1,5,11,2,5,11,2,11,11,1,11,15,2,5,15,1,5,15,1,11,15,2,11,5,2,5,6,2,5,6,2,6,5,2,6,5,1,5,5,2,5,5,2,6,5,1,6,6,2,5,6,1,5,6,1,6,6,2,6,5,1,5,6,1,5,6,2,5,5,2,5,5,2,6,6,2,6,6,1,6,5,1,6,10,2,5,11,2,5,11,2,6,10,2,6,10,1,5,10,2,5,10,2,6,10,1,6,11,2,5,11,1,5,11,1,6,11,2,6,10,1,5,11,1,5,11,2,5,10,2,5,10,2,6,11,2,6,11,1,6,10,1,6,5,2,10,6,2,10,6,2,11,5,2,11,5,1,10,5,2,10,5,2,11,5,1,11,6,2,10,6,1,10,6,1,11,6,2,11,5,1,10,6,1,10,6,2,10,5,2,10,5,2,11,6,2,11,6,1,11,5,1,11,10,2,10,11,2,10,11,2,11,10,2,11,10,1,10,10,2,10,10,2,11,10,1,11,11,2,10,11,1,10,11,1,11,11,2,11,10,1,10,11,1,10,11,2,10,10,2,10,10,2,11,11,2,11,11,1,11,10,1,11],"uv":[0.0156,0.0156,0.2344,0.0156,0.2344,0.2344,0.0156,0.2344,0.0156,0.0156,0.2344,0.0156,0.2344,0.2344,0.0156,0.2344,0.5,0.0156,0.5156,0.0156,0.5156,0.2344,0.5,0.2344,0.7344,0.0156,0.75,0.0156,0.75,0.2344,0.7344,0.2344,0.5156,0,0.7344,0,0.7344,0.0156,0.5156,0.0156,0.5156,0.2344,0.7344,0.2344,0.7344,0.25,0.5156,0.25,0.0156,0.1719,0.2344,0.1719,0.2344,0.2344,0.0156,0.2344,0.5156,0.1719,0.5313,0.1719,0.5313,0.2344,0.5156,0.2344,0.7188,0.1719,0.7344,0.1719,0.7344,0.2344,0.7188,0.2344,0.0156,0.2656,0.2344,0.2656,0.2344,0.2813,0.0156,0.2813,0.5156,0.2188,0.7344,0.2188,0.7344,0.2344,0.5156,0.2344,0.0156,0.0156,0.2344,0.0156,0.2344,0.0781,0.0156,0.0781,0.5156,0.0156,0.5313,0.0156,0.5313,0.0781,0.5156,0.0781,0.7188,0.0156,0.7344,0.0156,0.7344,0.0781,0.7188,0.0781,0.5156,0.0156,0.7344,0.0156,0.7344,0.0313,0.5156,0.0313,0.0156,0.4688,0.2344,0.4688,0.2344,0.4844,0.0156,0.4844,0.0156,0.0781,0.0781,0.0781,0.0781,0.1719,0.0156,0.1719,0.5156,0.0781,0.5313,0.0781,0.5313,0.1719,0.5156,0.1719,0.2188,0.3281,0.2344,0.3281,0.2344,0.4219,0.2188,0.4219,0.1719,0.0781,0.2344,0.0781,0.2344,0.1719,0.1719,0.1719,0.0156,0.3281,0.0313,0.3281,0.0313,0.4219,0.0156,0.4219,0.7188,0.0781,0.7344,0.0781,0.7344,0.1719,0.7188,0.1719,0.0781,0.0781,0.0938,0.0781,0.0938,0.0938,0.0781,0.0938,0.0156,0.3281,0.0313,0.3281,0.0313,0.3438,0.0156,0.3438,0.2188,0.3281,0.2344,0.3281,0.2344,0.3438,0.2188,0.3438,0.0781,0.2656,0.0938,0.2656,0.0938,0.2813,0.0781,0.2813,0.0781,0.4688,0.0938,0.4688,0.0938,0.4844,0.0781,0.4844,0.1563,0.0781,0.1719,0.0781,0.1719,0.0938,0.1563,0.0938,0.0156,0.3281,0.0313,0.3281,0.0313,0.3438,0.0156,0.3438,0.2188,0.3281,0.2344,0.3281,0.2344,0.3438,0.2188,0.3438,0.1563,0.2656,0.1719,0.2656,0.1719,0.2813,0.1563,0.2813,0.1563,0.4688,0.1719,0.4688,0.1719,0.4844,0.1563,0.4844,0.0781,0.1563,0.0938,0.1563,0.0938,0.1719,0.0781,0.1719,0.0156,0.4063,0.0313,0.4063,0.0313,0.4219,0.0156,0.4219,0.2188,0.4063,0.2344,0.4063,0.2344,0.4219,0.2188,0.4219,0.0781,0.2656,0.0938,0.2656,0.0938,0.2813,0.0781,0.2813,0.0781,0.4688,0.0938,0.4688,0.0938,0.4844,0.0781,0.4844,0.1563,0.1563,0.1719,0.1563,0.1719,0.1719,0.1563,0.1719,0.0156,0.4063,0.0313,0.4063,0.0313,0.4219,0.0156,0.4219,0.2188,0.4063,0.2344,0.4063,0.2344,0.4219,0.2188,0.4219,0.1563,0.2656,0.1719,0.2656,0.1719,0.2813,0.1563,0.2813,0.1563,0.4688,0.1719,0.4688,0.1719,0.4844,0.1563,0.4844],"shade":[0.5,1,0.6,0.6,0.8,0.8,1,0.6,0.6,0.8,0.8,1,0.6,0.6,0.8,0.8,1,0.6,0.6,1,0.6,0.6,1,0.6,0.6,0.8,0.8,1,0.6,0.6,0.8,0.8,1,0.6,0.6,0.8,0.8,1,0.6,0.6,0.8,0.8],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22,24,26,25,24,27,26,28,30,29,28,31,30,32,34,33,32,35,34,36,38,37,36,39,38,40,42,41,40,43,42,44,46,45,44,47,46,48,50,49,48,51,50,52,54,53,52,55,54,56,58,57,56,59,58,60,62,61,60,63,62,64,66,65,64,67,66,68,70,69,68,71,70,72,74,73,72,75,74,76,78,77,76,79,78,80,82,81,80,83,82,84,86,85,84,87,86,88,90,89,88,91,90,92,94,93,92,95,94,96,98,97,96,99,98,100,102,101,100,103,102,104,106,105,104,107,106,108,110,109,108,111,110,112,114,113,112,115,114,116,118,117,116,119,118,120,122,121,120,123,122,124,126,125,124,127,126,128,130,129,128,131,130,132,134,133,132,135,134,136,138,137,136,139,138,140,142,141,140,143,142,144,146,145,144,147,146,148,150,149,148,151,150,152,154,153,152,155,154,156,158,157,156,159,158,160,162,161,160,163,162,164,166,165,164,167,166]},{"tex":"cccloud:block/uplink_status_online","emit":true,"trans":false,"pos":[4,2.25,13,12,2.25,13,12,2.25,14,4,2.25,14,4,2,13,4,2.25,13,4,2.25,14,4,2,14,12,2.25,13,12,2,13,12,2,14,12,2.25,14,4,2,13,12,2,13,12,2.25,13,4,2.25,13,4,2.25,14,12,2.25,14,12,2,14,4,2,14],"uv":[0,0.5,0.5,0.5,0.5,0.5625,0,0.5625,0,0.5,0.5,0.5,0.5,0.5625,0,0.5625,0,0.5,0.5,0.5,0.5,0.5625,0,0.5625,0,0.5,0.5,0.5,0.5,0.5625,0,0.5625,0,0.5,0.5,0.5,0.5,0.5625,0,0.5625],"shade":[1,1,1,1,1],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18]}],"tall":1,"status":{"online":{},"connecting":{"cccloud:block/uplink_status_online":"cccloud:block/uplink_status_connecting"},"offline":{"cccloud:block/uplink_status_online":"cccloud:block/uplink_status_offline"}}},"bridge":{"parts":[{"tex":"cccloud:block/me_cloud_bridge","emit":false,"trans":false,"pos":[16,16,2,0,16,2,0,0,2,16,0,2,0,16,16,16,16,16,16,0,16,0,0,16,0,16,2,0,16,16,0,0,16,0,0,2,16,16,16,16,16,2,16,0,2,16,0,16,0,16,2,16,16,2,16,16,16,0,16,16,0,0,16,16,0,16,16,0,2,0,0,2,16,4,0,0,4,0,0,0,0,16,0,0,0,4,0,0,4,2,0,0,2,0,0,0,16,4,2,16,4,0,16,0,0,16,0,2,0,4,0,16,4,0,16,4,2,0,4,2,0,0,2,16,0,2,16,0,0,0,0,0,16,16,0,0,16,0,0,12,0,16,12,0,0,16,0,0,16,2,0,12,2,0,12,0,16,16,2,16,16,0,16,12,0,16,12,2,0,16,0,16,16,0,16,16,2,0,16,2,0,12,2,16,12,2,16,12,0,0,12,0,4,12,0,0,12,0,0,4,0,4,4,0,0,12,0,0,12,2,0,4,2,0,4,0,4,12,2,4,12,0,4,4,0,4,4,2,16,12,0,12,12,0,12,4,0,16,4,0,12,12,0,12,12,2,12,4,2,12,4,0,16,12,2,16,12,0,16,4,0,16,4,2,5,5,0,4,5,0,4,4,0,5,4,0,4,5,0,4,5,2,4,4,2,4,4,0,5,5,2,5,5,0,5,4,0,5,4,2,4,5,0,5,5,0,5,5,2,4,5,2,4,4,2,5,4,2,5,4,0,4,4,0,12,5,0,11,5,0,11,4,0,12,4,0,11,5,0,11,5,2,11,4,2,11,4,0,12,5,2,12,5,0,12,4,0,12,4,2,11,5,0,12,5,0,12,5,2,11,5,2,11,4,2,12,4,2,12,4,0,11,4,0,5,12,0,4,12,0,4,11,0,5,11,0,4,12,0,4,12,2,4,11,2,4,11,0,5,12,2,5,12,0,5,11,0,5,11,2,4,12,0,5,12,0,5,12,2,4,12,2,4,11,2,5,11,2,5,11,0,4,11,0,12,12,0,11,12,0,11,11,0,12,11,0,11,12,0,11,12,2,11,11,2,11,11,0,12,12,2,12,12,0,12,11,0,12,11,2,11,12,0,12,12,0,12,12,2,11,12,2,11,11,2,12,11,2,12,11,0,11,11,0],"uv":[0,0,0.25,0,0.25,0.25,0,0.25,0.25,0,0.5,0,0.5,0.25,0.25,0.25,0.2813,0,0.5,0,0.5,0.25,0.2813,0.25,0.25,0,0.4688,0,0.4688,0.25,0.25,0.25,0.25,0.0313,0.5,0.0313,0.5,0.25,0.25,0.25,0.25,0,0.5,0,0.5,0.2188,0.25,0.2188,0,0.1875,0.25,0.1875,0.25,0.25,0,0.25,0.25,0.1875,0.2813,0.1875,0.2813,0.25,0.25,0.25,0.4688,0.1875,0.5,0.1875,0.5,0.25,0.4688,0.25,0.75,0,1,0,1,0.0313,0.75,0.0313,0.25,0.2188,0.5,0.2188,0.5,0.25,0.25,0.25,0,0,0.25,0,0.25,0.0625,0,0.0625,0.25,0,0.2813,0,0.2813,0.0625,0.25,0.0625,0.4688,0,0.5,0,0.5,0.0625,0.4688,0.0625,0.25,0,0.5,0,0.5,0.0313,0.25,0.0313,0.75,0.2188,1,0.2188,1,0.25,0.75,0.25,0.1875,0.0625,0.25,0.0625,0.25,0.1875,0.1875,0.1875,0.25,0.0625,0.2813,0.0625,0.2813,0.1875,0.25,0.1875,0.9688,0.0625,1,0.0625,1,0.1875,0.9688,0.1875,0,0.0625,0.0625,0.0625,0.0625,0.1875,0,0.1875,0.75,0.0625,0.7813,0.0625,0.7813,0.1875,0.75,0.1875,0.4688,0.0625,0.5,0.0625,0.5,0.1875,0.4688,0.1875,0.1719,0.1719,0.1875,0.1719,0.1875,0.1875,0.1719,0.1875,0.75,0.1719,0.7813,0.1719,0.7813,0.1875,0.75,0.1875,0.9688,0.1719,1,0.1719,1,0.1875,0.9688,0.1875,0.8125,0,0.8281,0,0.8281,0.0313,0.8125,0.0313,0.8125,0.2188,0.8281,0.2188,0.8281,0.25,0.8125,0.25,0.0625,0.1719,0.0781,0.1719,0.0781,0.1875,0.0625,0.1875,0.75,0.1719,0.7813,0.1719,0.7813,0.1875,0.75,0.1875,0.9688,0.1719,1,0.1719,1,0.1875,0.9688,0.1875,0.9219,0,0.9375,0,0.9375,0.0313,0.9219,0.0313,0.9219,0.2188,0.9375,0.2188,0.9375,0.25,0.9219,0.25,0.1719,0.0625,0.1875,0.0625,0.1875,0.0781,0.1719,0.0781,0.75,0.0625,0.7813,0.0625,0.7813,0.0781,0.75,0.0781,0.9688,0.0625,1,0.0625,1,0.0781,0.9688,0.0781,0.8125,0,0.8281,0,0.8281,0.0313,0.8125,0.0313,0.8125,0.2188,0.8281,0.2188,0.8281,0.25,0.8125,0.25,0.0625,0.0625,0.0781,0.0625,0.0781,0.0781,0.0625,0.0781,0.75,0.0625,0.7813,0.0625,0.7813,0.0781,0.75,0.0781,0.9688,0.0625,1,0.0625,1,0.0781,0.9688,0.0781,0.9219,0,0.9375,0,0.9375,0.0313,0.9219,0.0313,0.9219,0.2188,0.9375,0.2188,0.9375,0.25,0.9219,0.25],"shade":[0.8,0.8,0.6,0.6,1,0.5,0.8,0.6,0.6,1,0.5,0.8,0.6,0.6,1,0.5,0.8,0.6,0.6,0.8,0.6,0.6,0.8,0.6,0.6,1,0.5,0.8,0.6,0.6,1,0.5,0.8,0.6,0.6,1,0.5,0.8,0.6,0.6,1,0.5],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22,24,26,25,24,27,26,28,30,29,28,31,30,32,34,33,32,35,34,36,38,37,36,39,38,40,42,41,40,43,42,44,46,45,44,47,46,48,50,49,48,51,50,52,54,53,52,55,54,56,58,57,56,59,58,60,62,61,60,63,62,64,66,65,64,67,66,68,70,69,68,71,70,72,74,73,72,75,74,76,78,77,76,79,78,80,82,81,80,83,82,84,86,85,84,87,86,88,90,89,88,91,90,92,94,93,92,95,94,96,98,97,96,99,98,100,102,101,100,103,102,104,106,105,104,107,106,108,110,109,108,111,110,112,114,113,112,115,114,116,118,117,116,119,118,120,122,121,120,123,122,124,126,125,124,127,126,128,130,129,128,131,130,132,134,133,132,135,134,136,138,137,136,139,138,140,142,141,140,143,142,144,146,145,144,147,146,148,150,149,148,151,150,152,154,153,152,155,154,156,158,157,156,159,158,160,162,161,160,163,162,164,166,165,164,167,166]},{"tex":"cccloud:block/cloud_bridge_status_online","emit":true,"trans":false,"pos":[11,10,1.75,5,10,1.75,5,6,1.75,11,6,1.75,12,3,-0.25,4,3,-0.25,4,2,-0.25,12,2,-0.25,4,3,-0.25,4,3,0,4,2,0,4,2,-0.25,12,3,0,12,3,-0.25,12,2,-0.25,12,2,0,4,3,-0.25,12,3,-0.25,12,3,0,4,3,0,4,2,0,12,2,0,12,2,-0.25,4,2,-0.25],"uv":[0,0,0.375,0,0.375,0.25,0,0.25,0,0.5,0.5,0.5,0.5,0.5625,0,0.5625,0,0.5,0.5,0.5,0.5,0.5625,0,0.5625,0,0.5,0.5,0.5,0.5,0.5625,0,0.5625,0,0.5,0.5,0.5,0.5,0.5625,0,0.5625,0,0.5,0.5,0.5,0.5,0.5625,0,0.5625],"shade":[1,1,1,1,1,1],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22]},{"tex":"cccloud:block/me_cloud_bridge","emit":true,"trans":false,"pos":[7,9,16.1,9,9,16.1,9,7,16.1,7,7,16.1,7,9,16,7,9,16.1,7,7,16.1,7,7,16,9,9,16.1,9,9,16,9,7,16,9,7,16.1,7,9,16,9,9,16,9,9,16.1,7,9,16.1,7,7,16.1,9,7,16.1,9,7,16,7,7,16,0,9,7,-0.1,9,7,-0.1,7,7,0,7,7,-0.1,9,9,0,9,9,0,7,9,-0.1,7,9,-0.1,9,7,-0.1,9,9,-0.1,7,9,-0.1,7,7,-0.1,9,7,0,9,7,0,9,9,-0.1,9,9,-0.1,7,9,0,7,9,0,7,7,-0.1,7,7,16.1,9,7,16,9,7,16,7,7,16.1,7,7,16,9,9,16.1,9,9,16.1,7,9,16,7,9,16.1,9,9,16.1,9,7,16.1,7,7,16.1,7,9,16,9,7,16.1,9,7,16.1,9,9,16,9,9,16,7,9,16.1,7,9,16.1,7,7,16,7,7,9,16.1,7,7,16.1,7,7,16,7,9,16,7,7,16.1,9,9,16.1,9,9,16,9,7,16,9,7,16.1,7,7,16.1,9,7,16,9,7,16,7,9,16.1,9,9,16.1,7,9,16,7,9,16,9,7,16.1,7,9,16.1,7,9,16.1,9,7,16.1,9,9,0,7,7,0,7,7,-0.1,7,9,-0.1,7,7,0,9,9,0,9,9,-0.1,9,7,-0.1,9,7,0,7,7,0,9,7,-0.1,9,7,-0.1,7,9,0,9,9,0,7,9,-0.1,7,9,-0.1,9,7,-0.1,9,9,-0.1,9,9,-0.1,7,7,-0.1,7],"uv":[0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406,0.6094,0.1094,0.6406,0.1094,0.6406,0.1406,0.6094,0.1406],"shade":[1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1],"idx":[0,2,1,0,3,2,4,6,5,4,7,6,8,10,9,8,11,10,12,14,13,12,15,14,16,18,17,16,19,18,20,22,21,20,23,22,24,26,25,24,27,26,28,30,29,28,31,30,32,34,33,32,35,34,36,38,37,36,39,38,40,42,41,40,43,42,44,46,45,44,47,46,48,50,49,48,51,50,52,54,53,52,55,54,56,58,57,56,59,58,60,62,61,60,63,62,64,66,65,64,67,66,68,70,69,68,71,70,72,74,73,72,75,74,76,78,77,76,79,78,80,82,81,80,83,82,84,86,85,84,87,86,88,90,89,88,91,90,92,94,93,92,95,94,96,98,97,96,99,98]}],"tall":1,"status":{"online":{},"connecting":{"cccloud:block/cloud_bridge_status_online":"cccloud:block/cloud_bridge_status_connecting"},"offline":{"cccloud:block/cloud_bridge_status_online":"cccloud:block/cloud_bridge_status_offline"}}}}`)};function $l(e,t,n){let r=new Map;for(let i of e){let e=Math.ceil(t/16),a=Math.floor(i.x/16),o=Math.floor(i.y/16),s=Math.floor(i.z/16);for(let c=-e;c<=e;c++)for(let l=-e;l<=e;l++)for(let u=-e;u<=e;u++){let e=a+c,d=o+l,f=s+u;if(d<-4||d>19)continue;let p=(e,t)=>Math.max(e*16-t,0,t-(e*16+15));if(Math.hypot(p(e,i.x),p(d,i.y),p(f,i.z))>t)continue;let m=`${e},${d},${f}`,h=Math.hypot(e*16+8-n.x,d*16+8-n.y,f*16+8-n.z),g=r.get(m);(!g||h<g[3])&&r.set(m,[e,d,f,h])}}return[...r.values()].sort((e,t)=>e[3]-t[3]).map(([e,t,n])=>[e,t,n])}function eu(){let e=document.createElement(`canvas`);e.width=e.height=64;let t=e.getContext(`2d`),n=t.createRadialGradient(32,32,0,32,32,32);n.addColorStop(0,`rgba(255,255,255,1)`),n.addColorStop(.35,`rgba(255,255,255,0.35)`),n.addColorStop(1,`rgba(255,255,255,0)`),t.fillStyle=n,t.fillRect(0,0,64,64);let r=new Ki(e);return r.colorSpace=Xe,r}var tu=class{group=new In;worker;regions=new Map;queue=[];inflight=0;stamps=[];pausedUntil=0;timer=0;disposed=!1;src=null;triangles=0;loaded=0;total=0;unavailable=!1;onChange=()=>{};clip=new Hr(new G(0,-1,0),1e4);glowTex=eu();mats;constructor(){let e=[this.clip];this.mats={opaque:new Xr({vertexColors:!0,clippingPlanes:e}),light:new Xr({vertexColors:!0,clippingPlanes:e}),water:new Xr({vertexColors:!0,transparent:!0,opacity:.58,depthWrite:!1,side:2,clippingPlanes:e}),glass:new Xr({vertexColors:!0,transparent:!0,opacity:.4,depthWrite:!1,side:2,clippingPlanes:e}),glow:new Ri({size:3.2,sizeAttenuation:!0,map:this.glowTex,vertexColors:!0,transparent:!0,opacity:.45,depthWrite:!1,blending:2,clippingPlanes:e})},this.worker=new Worker(new URL(`/assets/voxelWorker-DgEmBfdT.js`,``+import.meta.url),{type:`module`}),this.worker.onmessage=e=>{e.data.type===`region`&&this.applyRegion(e.data.key,e.data.mesh)}}load(e){let t=this.src&&this.src.mf===e.mf&&this.src.dim===e.dim&&this.src.radius===e.radius;this.src=e,t||(this.clear(),this.worker.postMessage({type:`reset`}),this.queue=$l(e.nodes,e.radius,e.focus),this.total=this.queue.length,this.loaded=0),this.pump()}setClip(e){this.clip.constant=e===null?1e4:e}setNight(e){let t=e?.42:1;this.mats.opaque.color=new Y(t,t,t*1.15),this.mats.water.color=new Y(t,t,t*1.2),this.mats.glass.color=new Y(t,t,t),this.mats.glow.opacity=e?.95:.35,this.mats.glow.size=e?4.2:3.2}pump(){if(clearTimeout(this.timer),this.disposed||!this.src||this.unavailable)return;let e=performance.now();for(this.stamps=this.stamps.filter(t=>e-t<1e3);this.queue.length&&this.inflight<4&&this.stamps.length<35&&e>=this.pausedUntil&&this.triangles<this.src.budget;){let t=this.queue.shift();this.stamps.push(e),this.inflight++,this.fetchSection(t).finally(()=>{this.inflight--,this.pump()})}this.queue.length&&this.triangles<this.src.budget&&(this.timer=window.setTimeout(()=>this.pump(),Math.max(30,this.pausedUntil-e)))}async fetchSection([e,t,n]){let r=this.src;try{let i=await fetch(`${r.base.replace(/\/$/,``)}/api/mainframes/${encodeURIComponent(r.mf)}/voxels/${r.dim}/${e}/${t}/${n}`,{headers:{authorization:`Bearer ${r.token}`}});if(this.disposed||r!==this.src)return;if(i.status===429){this.queue.unshift([e,t,n]),this.pausedUntil=performance.now()+1e3;return}if(this.loaded++,i.status===404||i.status===401){this.unavailable=!0,this.onChange();return}if(i.status!==200)return this.onChange();let a=await i.json();if(!a||!Array.isArray(a.palette)||typeof a.runs!=`string`)return;if(a.exposed===0)return this.onChange();this.worker.postMessage({type:`section`,cx:e,cy:t,cz:n,palette:a.palette,runs:a.runs})}catch{}}geometry(e){let t=new Rr;return t.setAttribute(`position`,new X(e.pos,3)),t.setAttribute(`color`,new X(e.col,3,!0)),t.setIndex(new X(e.idx,1)),t.computeBoundingSphere(),t}applyRegion(e,t){if(this.disposed)return;this.dropRegion(e);let n=[];for(let[e,r,i]of[[`opaque`,this.mats.opaque,0],[`light`,this.mats.light,0],[`glass`,this.mats.glass,2],[`water`,this.mats.water,3]]){let a=t[e];if(!a.idx.length)continue;let o=new ci(this.geometry(a),r);o.renderOrder=i,o.matrixAutoUpdate=!1,n.push(o),this.group.add(o)}let r=null;if(t.glow.pos.length){let e=new Rr;e.setAttribute(`position`,new X(t.glow.pos,3)),e.setAttribute(`color`,new X(t.glow.col,3,!0)),r=new Ui(e,this.mats.glow),r.renderOrder=4,this.group.add(r)}this.regions.set(e,{meshes:n,glow:r,tris:t.triangles}),this.triangles+=t.triangles,this.onChange()}dropRegion(e){let t=this.regions.get(e);if(t){for(let e of t.meshes)e.geometry.dispose(),this.group.remove(e);t.glow&&(t.glow.geometry.dispose(),this.group.remove(t.glow)),this.triangles-=t.tris,this.regions.delete(e)}}get triangleCount(){return this.triangles}get regionCount(){return this.regions.size}clear(){for(let e of[...this.regions.keys()])this.dropRegion(e);this.queue=[]}dispose(){this.disposed=!0,clearTimeout(this.timer),this.clear(),this.worker.terminate();for(let e of Object.values(this.mats))e.dispose();this.glowTex.dispose()}},nu=Ql,ru=new Map;function iu(e,t){let n=`${e}|${t}`,r=ru.get(n);if(r)return r;let i=nu.models[e].parts[t];r=new Rr;let a=new Float32Array(i.pos.length);for(let e=0;e<i.pos.length;e+=3)a[e]=i.pos[e]/16-.5,a[e+1]=i.pos[e+1]/16,a[e+2]=i.pos[e+2]/16-.5;let o=new Float32Array(i.uv.length);for(let e=0;e<i.uv.length;e+=2)o[e]=i.uv[e],o[e+1]=1-i.uv[e+1];let s=new Float32Array(i.pos.length/3*3);for(let e=0;e<i.shade.length;e++)for(let t=0;t<4;t++)s.fill(i.shade[e],(e*4+t)*3,(e*4+t)*3+3);return r.setAttribute(`position`,new X(a,3)),r.setAttribute(`uv`,new X(o,2)),r.setAttribute(`color`,new X(s,3)),r.setIndex(i.idx),r.computeBoundingSphere(),ru.set(n,r),r}var au=null;function ou(e,t){au??=new Map;let n=au.get(e);if(n)return n;let r=new Image;return n=new nn(r),n.magFilter=v,n.minFilter=v,n.generateMipmaps=!1,n.colorSpace=Xe,r.onload=()=>{n.needsUpdate=!0,t()},r.src=nu.textures[e]??``,au.set(e,n),n}var su=new Map;function cu(e,t,n){let r=`${e}|${t.emit}|${t.trans}`,i=su.get(r);return i||(i=new Xr({map:ou(e,n),vertexColors:!0,transparent:t.trans,alphaTest:t.trans?0:.5,depthWrite:!t.trans,side:2}),su.set(r,i),i)}function lu(){let e=document.createElement(`canvas`);e.width=e.height=32;let t=e.getContext(`2d`);t.fillStyle=`#0a0c0f`,t.beginPath(),t.arc(16,16,15,0,Math.PI*2),t.fill(),t.fillStyle=`#fff`,t.beginPath(),t.arc(16,16,11,0,Math.PI*2),t.fill();let n=new Ki(e);return n.colorSpace=Xe,n}var uu=(e,t,n)=>getComputedStyle(e).getPropertyValue(t).trim()||n,du=class{renderer;scene=new Gn;camera=new Ua(50,1,.3,8e3);controls;content=new In;terrainGroup=new In;meshes=[];nodeIdx=new Map;nodes=[];stones=[];markers=null;selBox;labels;labelEls=new Map;dashed=[];scale=1;pending=0;anim=0;ro;props;fittedFor=``;down=null;dot=lu();owned=[];voxels=null;statusTimer=0;host;constructor(e,t){this.host=e,this.props=t;let n=matchMedia(`(max-width: 719px)`).matches;this.renderer=new kl({antialias:!n||devicePixelRatio<2,powerPreference:`default`,alpha:!1}),this.renderer.setPixelRatio(Math.min(devicePixelRatio||1,n?1.5:2)),this.renderer.localClippingEnabled=!0,this.renderer.domElement.className=`n3d-canvas`,this.renderer.domElement.tabIndex=0,this.renderer.domElement.setAttribute(`aria-label`,`3D network view. Drag to rotate, two fingers or right mouse button to pan, scroll or pinch to zoom, arrow keys pan.`),this.renderer.domElement.setAttribute(`role`,`img`),e.prepend(this.renderer.domElement),this.labels=document.createElement(`div`),this.labels.className=`n3d-labels`,e.appendChild(this.labels),this.scene.background=new Y(uu(e,`--screen`,`#0a0c0f`)),this.scene.add(this.terrainGroup,this.content);let r=new ta(new Xi(1.25,1.25,1.25));r.translate(0,.5,0),this.selBox=new Li(r,new Ti({color:6211813,depthTest:!1,transparent:!0})),this.selBox.renderOrder=10,this.selBox.visible=!1,this.scene.add(this.selBox),this.controls=new zl(this.camera,this.renderer.domElement),this.controls.maxPolarAngle=Math.PI*.495,this.controls.minDistance=3,this.controls.maxDistance=3e3,this.controls.zoomToCursor=!0,this.controls.listenToKeyEvents(this.renderer.domElement),this.controls.keyPanSpeed=20,this.controls.addEventListener(`change`,()=>this.onCamera());let i=this.renderer.domElement;i.addEventListener(`webglcontextlost`,e=>{e.preventDefault(),this.props.onFail(`context_lost`)}),i.addEventListener(`pointerdown`,e=>this.down={x:e.clientX,y:e.clientY,t:performance.now()}),i.addEventListener(`pointerup`,e=>{let t=this.down;this.down=null,!(!t||Math.hypot(e.clientX-t.x,e.clientY-t.y)>6||performance.now()-t.t>700)&&this.pick(e.clientX,e.clientY,e.pointerType===`touch`?30:20)}),i.addEventListener(`keydown`,e=>{e.key===`+`||e.key===`=`?this.zoom(.75):e.key===`-`?this.zoom(1/.75):e.key===`Escape`&&this.props.onSelect(void 0)}),this.ro=new ResizeObserver(()=>this.resize()),this.ro.observe(e),this.resize()}request=()=>{this.pending||=requestAnimationFrame(()=>{this.pending=0,this.render()})};render(){this.renderer.render(this.scene,this.camera),this.updateLabels(),this.host.dataset.drawCalls=String(this.renderer.info.render.calls)}resize(){let e=this.host.clientWidth||1,t=this.host.clientHeight||1;this.renderer.setSize(e,t,!1),this.camera.aspect=e/t,this.camera.updateProjectionMatrix(),this.request()}onCamera(){let e=this.camera.position.distanceTo(this.controls.target),t=Math.max(1,Math.min(8,e/45));Math.abs(t-this.scale)/this.scale>.12&&(this.scale=t,this.placeInstances()),this.request()}update(e,t){this.props=e,this.updateVoxels(e,t);let n=!t||t.net!==e.net||t.dim!==e.dim||t.ranges!==e.ranges||t.waystones!==e.waystones;(!t||t.terrain!==e.terrain||t.dim!==e.dim)&&this.buildTerrain(),n?this.build():t.selected!==e.selected&&this.highlight(),this.fittedFor!==e.dim&&(this.fittedFor=e.dim,this.fit(!1)),t&&e.flyKey!==t.flyKey&&e.selected&&this.flyTo(e.selected),this.request()}updateVoxels(e,t){e.voxels?(this.voxels||(this.voxels=new tu,this.scene.add(this.voxels.group),this.voxels.onChange=()=>{this.request(),clearTimeout(this.statusTimer),this.statusTimer=window.setTimeout(()=>{let e=this.voxels;e&&this.props.onVoxelStatus?.({loaded:e.loaded,total:e.total,triangles:e.triangleCount,unavailable:e.unavailable})},120)}),(!t?.voxels||t.voxels.dim!==e.voxels.dim||t.voxels.mf!==e.voxels.mf||t.voxels.radius!==e.voxels.radius)&&this.voxels.load(e.voxels),this.voxels.group.visible=!0,this.voxels.setClip(e.clipY),this.voxels.setNight(e.night)):this.voxels&&(this.voxels.group.visible=!1);let n=!!e.voxels&&!this.voxels?.unavailable;this.terrainGroup.visible=!n;let r=n?e.night?`#070b14`:`#6f93bb`:uu(this.host,`--screen`,`#0a0c0f`);this.scene.background.set(r),this.scene.fog=n?new Wn(r,e.night?.0045:.003):null}clear(){for(let e of this.owned)e.dispose();this.owned=[],this.content.clear(),this.meshes=[],this.nodeIdx.clear(),this.markers=null,this.dashed=[]}build(){this.clear();let{net:e,dim:t,ranges:n}=this.props;this.nodes=e.nodes.filter(e=>e.dim===t&&e.pos);for(let e of r(this.nodes,nu.models)){let t=nu.models[e.model].parts[e.part],n=new bi(iu(e.model,e.part),cu(e.tex,t,this.request),e.nodes.length);n.frustumCulled=!1,t.trans&&(n.renderOrder=2),e.nodes.forEach((e,t)=>{let r=this.nodeIdx.get(e)??[];r.push([n,t]),this.nodeIdx.set(e,r)}),this.meshes.push(n),this.content.add(n)}let i=new Float32Array(this.nodes.length*3),a=new Float32Array(this.nodes.length*3),o=new Y;this.nodes.forEach((e,t)=>{o.setHex(l[e.status]??8226704),a.set([o.r,o.g,o.b],t*3)});let s=new Rr;s.setAttribute(`position`,new X(i,3)),s.setAttribute(`color`,new X(a,3));let c=new Ri({size:11,sizeAttenuation:!1,vertexColors:!0,map:this.dot,alphaTest:.4,depthTest:!1,transparent:!0});if(this.markers=new Ui(s,c),this.markers.renderOrder=5,this.markers.frustumCulled=!1,this.content.add(this.markers),this.owned.push(s,c),this.stones=(this.props.waystones??[]).filter(e=>e.dim===t).slice(0,128),this.stones.length){let e=new Float32Array(this.stones.length*3);this.stones.forEach((t,n)=>e.set([t.x+.5,t.y+1.6,t.z+.5],n*3));let t=new Rr;t.setAttribute(`position`,new X(e,3));let n=new Ri({size:13,sizeAttenuation:!1,color:12750079,map:this.dot,alphaTest:.4,depthTest:!1,transparent:!0}),r=new Ui(t,n);r.renderOrder=5,r.frustumCulled=!1,this.content.add(r),this.owned.push(t,n)}this.placeInstances(),this.buildLinks(),n&&this.buildRanges(),this.highlight(),this.syncLabels()}anchor(e,t=new G){let n=nu.models[s[e.type]]?.tall??1;return t.set(e.pos.x+.5,e.pos.y+n*.5*this.scale,e.pos.z+.5)}placeInstances(){let e=new J,t=this.scale;this.nodes.forEach((n,r)=>{e.makeScale(t,t,t).setPosition(n.pos.x+.5,n.pos.y,n.pos.z+.5);for(let[t,n]of this.nodeIdx.get(r)??[])t.setMatrixAt(n,e)});for(let e of this.meshes)e.instanceMatrix.needsUpdate=!0;if(this.markers){let e=this.markers.geometry.getAttribute(`position`);this.nodes.forEach((n,r)=>{let i=nu.models[s[n.type]]?.tall??1;e.setXYZ(r,n.pos.x+.5,n.pos.y+(i+.7)*t,n.pos.z+.5)}),e.needsUpdate=!0}for(let e of this.dashed)e.dashSize=e.userData.dash*t,e.gapSize=e.userData.gap*t;this.placeSelection()}buildLinks(){let e=c(this.props.net,this.props.dim),t={wired:{pos:[],col:[],ids:[]},radio:{pos:[],col:[],ids:[]},spare:{pos:[],col:[],ids:[]},ender:{pos:[],col:[],ids:[]},legacy:{pos:[],col:[],ids:[]}},n=new G,r=new G,i=new Y;for(let a of e){let e=t[a.link.legacy?`legacy`:a.link.kind===`wired`?`wired`:a.link.kind===`ender`?`ender`:a.used?`radio`:`spare`];n.set(a.a.pos.x+.5,a.a.pos.y+.5,a.a.pos.z+.5),r.set(a.b.pos.x+.5,a.b.pos.y+.5,a.b.pos.z+.5),e.pos.push(n.x,n.y,n.z,r.x,r.y,r.z),i.setHex(d[a.cls]??6211813),e.col.push(i.r,i.g,i.b,i.r,i.g,i.b),e.ids.push([a.a.id,a.b.id])}let a=(e,n)=>{let r=t[e];if(!r.pos.length)return;let i=new Rr;i.setAttribute(`position`,new Er(r.pos,3)),i.setAttribute(`color`,new Er(r.col,3)),i.userData.base=Float32Array.from(r.col),i.userData.ids=r.ids;let a=new Li(i,n);a.frustumCulled=!1,n.depthTest=!1,n.transparent=!0,a.renderOrder=4,n instanceof ga&&(a.computeLineDistances(),this.dashed.push(n)),this.content.add(a),this.owned.push(i,n)},o=(e,t,n=1)=>{let r=new ga({vertexColors:!0,dashSize:e*this.scale,gapSize:t*this.scale,transparent:n<1,opacity:n});return r.userData={dash:e,gap:t},r};a(`wired`,new Ti({vertexColors:!0})),a(`radio`,o(2.2,1.4)),a(`spare`,o(.8,1.6,.35)),a(`ender`,o(.4,1.2)),a(`legacy`,o(3,1.5))}buildRanges(){let e=this.nodes.filter(e=>f(e.type)&&e.status===`online`&&e.type!==`ender_relay`);if(!e.length)return;let t=new ra(1,40,20),n=new Xr({color:6211813,transparent:!0,opacity:.05,depthWrite:!1,side:2}),r=new bi(t,n,e.length),i=new J,a=new Y;e.forEach((e,t)=>{i.makeScale(e.range,e.range,e.range).setPosition(e.pos.x+.5,e.pos.y+.5,e.pos.z+.5),r.setMatrixAt(t,i),r.setColorAt(t,a.setHex(e.type===`mainframe`?14538609:6211813))}),r.renderOrder=1,r.frustumCulled=!1;let o=[],s=[];for(let t of e){a.setHex(t.type===`mainframe`?14538609:6211813);for(let e=0;e<72;e++){let n=e/72*Math.PI*2,r=(e+1)/72*Math.PI*2;o.push(t.pos.x+.5+Math.cos(n)*t.range,t.pos.y+.5,t.pos.z+.5+Math.sin(n)*t.range),o.push(t.pos.x+.5+Math.cos(r)*t.range,t.pos.y+.5,t.pos.z+.5+Math.sin(r)*t.range),s.push(a.r,a.g,a.b,a.r,a.g,a.b)}}let c=new Rr;c.setAttribute(`position`,new Er(o,3)),c.setAttribute(`color`,new Er(s,3));let l=new Ti({vertexColors:!0,transparent:!0,opacity:.45,depthWrite:!1}),u=new Li(c,l);u.frustumCulled=!1,this.content.add(r,u),this.owned.push(t,n,c,l)}buildTerrain(){for(let e of this.terrainGroup.children)e.geometry.dispose(),e.material.dispose();this.terrainGroup.clear();let e=this.props.terrain,t=this.props.net.nodes.filter(e=>e.dim===this.props.dim&&e.pos);if(e&&e.dim===this.props.dim&&e.patches.length){let t=new Xr({vertexColors:!0,side:2});e.patches.forEach((n,r)=>{let i=o(n,e.step),a=new Rr;a.setAttribute(`position`,new X(i.pos,3)),a.setAttribute(`color`,new X(i.col,3)),a.setIndex(new X(i.idx,1));let s=new ci(a,r===0?t:t.clone());this.terrainGroup.add(s)})}else{let e=a(t,!0),n=Math.ceil(e.r*2.2/16)*16,r=new co(n,n/16,2785177,1909804),i=t.length?Math.min(...t.map(e=>e.pos.y)):64;r.position.set(Math.round(e.cx/16)*16,i-.01,Math.round(e.cz/16)*16),this.terrainGroup.add(r)}this.request()}highlight(){let e=this.props.selected,t=e?this.props.net.nodes.find(t=>t.id===e):void 0,n=t?i(this.props.net,t.id):null,r=new Y(1,1,1),a=new Y(.32,.32,.36);this.nodes.forEach((e,t)=>{for(let[i,o]of this.nodeIdx.get(t)??[])i.setColorAt(o,!n||n.has(e.id)?r:a)});for(let e of this.meshes)e.instanceColor&&(e.instanceColor.needsUpdate=!0);for(let e of this.content.children){if(!(e instanceof Li)||!e.geometry.userData.ids)continue;let t=e.geometry,r=t.userData.base,i=t.getAttribute(`color`);t.userData.ids.forEach(([e,t],a)=>{let o=!n||n.has(e)&&n.has(t)?1:.22;for(let e=0;e<6;e++)i.array[a*6+e]=r[a*6+e]*o}),i.needsUpdate=!0}this.placeSelection(),this.syncLabels(),this.request()}placeSelection(){let e=this.props.selected?this.nodes.find(e=>e.id===this.props.selected):void 0;if(this.selBox.visible=!!e,!e)return;let t=nu.models[s[e.type]]?.tall??1;this.selBox.position.set(e.pos.x+.5,e.pos.y-.125*this.scale,e.pos.z+.5),this.selBox.scale.set(this.scale,this.scale*t,this.scale)}pick(e,t,n){let r=this.renderer.domElement.getBoundingClientRect(),i=new G,a=this.nodes.map((e,t)=>(this.anchor(e,i).project(this.camera),{i:t,x:(i.x+1)/2*r.width,y:(1-i.y)/2*r.height,depth:i.z})),o=u(a,e-r.left,t-r.top,n);this.props.onSelect(o===void 0?void 0:this.nodes[o].id)}syncLabels(){let e=this.nodes.filter(e=>f(e.type)||e.id===this.props.selected).slice(0,60),t=new Set(e.map(e=>e.id));for(let[e,n]of this.labelEls)t.has(e)||(n.remove(),this.labelEls.delete(e));for(let t of e){let e=this.labelEls.get(t.id);e||(e=document.createElement(`div`),e.className=`n3d-label`,this.labels.appendChild(e),this.labelEls.set(t.id,e)),e.textContent=t.name||`Device`,e.classList.toggle(`sel`,t.id===this.props.selected),e.classList.toggle(`off`,t.status!==`online`)}this.stones.forEach((e,n)=>{let r=`ws:${n}`;t.add(r);let i=this.labelEls.get(r);i||(i=document.createElement(`div`),i.className=`n3d-label ws`,this.labels.appendChild(i),this.labelEls.set(r,i)),i.textContent=e.name});for(let[e,n]of this.labelEls)t.has(e)||(n.remove(),this.labelEls.delete(e))}updateLabels(){let e=this.host.clientWidth,t=this.host.clientHeight,n=new G;for(let[r,i]of this.labelEls){let a=r.startsWith(`ws:`)?this.stones[Number(r.slice(3))]:void 0,o=a?void 0:this.nodes.find(e=>e.id===r);if(a)n.set(a.x+.5,a.y+1.6,a.z+.5).project(this.camera);else if(o){let e=nu.models[s[o.type]]?.tall??1;n.set(o.pos.x+.5,o.pos.y+(e+.7)*this.scale,o.pos.z+.5).project(this.camera)}else continue;let c=n.z>-1&&n.z<1&&Math.abs(n.x)<1.1&&Math.abs(n.y)<1.1;i.hidden=!c,c&&(i.style.transform=`translate(${Math.round((n.x+1)/2*e)}px, ${Math.round((1-n.y)/2*t)-10}px) translate(-50%, -100%)`)}}fit(e){let t=a(this.nodes,!1),n=Math.max(40,t.r*1.7);this.moveTo(new G(t.cx,t.cy,t.cz),new G(t.cx,t.cy+n*.6,t.cz+n*.8),e)}flyTo(e){let t=this.nodes.find(t=>t.id===e);if(!t)return;let n=new G(t.pos.x+.5,t.pos.y+.5,t.pos.z+.5),r=this.camera.position.clone().sub(this.controls.target).normalize(),i=f(t.type)?22:14;this.moveTo(n,n.clone().add(r.multiplyScalar(i)),!0)}zoom(e){let t=this.camera.position.clone().sub(this.controls.target).multiplyScalar(e),n=Math.max(this.controls.minDistance,Math.min(this.controls.maxDistance,t.length()));this.camera.position.copy(this.controls.target).add(t.setLength(n)),this.controls.update()}moveTo(e,t,n){cancelAnimationFrame(this.anim);let r=matchMedia(`(prefers-reduced-motion: reduce)`).matches;if(!n||r){this.controls.target.copy(e),this.camera.position.copy(t),this.controls.update();return}let i=this.controls.target.clone(),a=this.camera.position.clone(),o=performance.now(),s=()=>{let n=Math.min(1,(performance.now()-o)/650),r=n<.5?2*n*n:1-(-2*n+2)**2/2;this.controls.target.lerpVectors(i,e,r),this.camera.position.lerpVectors(a,t,r),this.controls.update(),n<1&&(this.anim=requestAnimationFrame(s))};this.anim=requestAnimationFrame(s)}dispose(){cancelAnimationFrame(this.anim),clearTimeout(this.statusTimer),this.voxels?.dispose(),cancelAnimationFrame(this.pending),this.ro.disconnect(),this.controls.dispose(),this.clear();for(let e of this.terrainGroup.children){let t=e;t.geometry?.dispose(),t.material?.dispose()}this.selBox.geometry.dispose(),this.selBox.material.dispose(),this.dot.dispose(),this.renderer.dispose(),this.renderer.domElement.remove(),this.labels.remove()}};function fu(r){let i=e(null),a=e(null),o=e(void 0);return t(()=>{try{a.current=new du(i.current,r)}catch(e){r.onFail(String(e?.message??e));return}return()=>{a.current?.dispose(),a.current=null,o.current=void 0}},[]),t(()=>{a.current&&(a.current.update(r,o.current),o.current=r)}),n(`div`,{class:`n3d`,ref:i,children:n(`div`,{class:`pz-controls`,children:[n(`button`,{type:`button`,class:`pz-btn`,"aria-label":`Zoom in`,onClick:()=>a.current?.zoom(.7),children:`+`}),n(`button`,{type:`button`,class:`pz-btn`,"aria-label":`Zoom out`,onClick:()=>a.current?.zoom(1/.7),children:`−`}),n(`button`,{type:`button`,class:`pz-btn pz-fit`,"aria-label":`Fit to screen`,onClick:()=>a.current?.fit(!0),children:`⤢`})]})})}export{fu as default};