# Upload de foto do veículo — `POST /api/v1/vehicles/{id}/photo`

> Issue relacionada: [flow-fuel-api#10](https://github.com/Rochafelip/flow-fuel-api/issues/10)
> Spec do client Android: `docs/superpowers/specs/2026-07-03-vehicle-photo-required-design.md` (repo `flowfuel-app`)

## Por quê

O app Android passou a exigir uma foto do veículo na criação. O client já
chama `POST /api/v1/vehicles/{id}/photo` logo após criar o veículo
(`POST /api/v1/vehicles`, que já existe e não muda). Esse endpoint de foto
**ainda não existe no backend** — hoje toda tentativa de upload recebe 404,
o veículo fica criado mas sem foto, e o app mostra "Tentar novamente" no
client (comportamento esperado e já tratado no app; não precisa de nada do
lado do backend para esse fallback funcionar, só precisa que o endpoint
passe a existir para o fluxo fechar de verdade).

## Padrão a seguir: espelhar o upload de foto de perfil

Já existe um endpoint praticamente idêntico para foto de perfil de usuário —
usar exatamente o mesmo padrão de storage, validação e resposta, só trocando
o dono do recurso (veículo em vez de usuário).

| | Foto de perfil (já existe) | Foto de veículo (a criar) |
|---|---|---|
| Endpoint | `POST /api/v1/auth/{userId}/upload-profile-picture` | `POST /api/v1/vehicles/{id}/photo` |
| Storage | S3 / Backblaze B2 | S3 / Backblaze B2 (mesmo bucket/config) |
| Autorização | dono do próprio usuário (`ForbiddenNotSelf`) | dono do veículo (`ForbiddenNotOwner`, mesmo padrão já usado em `GET/PUT/DELETE /vehicles/{id}`) |
| Campo de resposta | `internalUrl`, `signedUrl` | mesmo formato — mas o valor final deve popular `VehicleResponseDTO.photo` |

## Contrato

```
POST /api/v1/vehicles/{id}/photo
Content-Type: multipart/form-data

Request:
  file: binary (obrigatório)

Response 200 OK:
  {
    "internalUrl": "/api/v1/vehicles/{id}/photo",
    "signedUrl": "https://...>"   // presignada, mesma janela de expiração usada no perfil (15 min)
  }
```

### Validações (idênticas às da foto de perfil)

- Arquivo ausente → `400`, `BUSINESS_RULE_VIOLATED`, `"Arquivo não informado"`
- Tipo inválido (permitir só JPEG/PNG/WEBP) → `400`, `BUSINESS_RULE_VIOLATED`, `"Tipo de arquivo inválido. Permitido: JPEG, PNG, WEBP"`
- Tamanho > 5MB → `400`, `BUSINESS_RULE_VIOLATED`, `"Arquivo excede o tamanho máximo de 5 MB"`
- Veículo não pertence ao usuário autenticado → `403` (`ForbiddenNotOwner`, mesmo padrão de `PUT /vehicles/{id}`)
- Veículo não existe → `404`

> Atenção ao mesmo ponto cego já observado no endpoint de foto de perfil: o
> limite de multipart do servlet
> (`spring.servlet.multipart.max-file-size` / `max-request-size`) pode
> rejeitar o request *antes* de chegar na camada de validação, e hoje não
> há handler para `MultipartException` no `GlobalExceptionHandler` — o
> comportamento observado pode ser `500` em vez do `400` esperado para
> arquivo grande demais. Vale aproveitar esta implementação para adicionar
> esse handler (beneficia os dois endpoints).

### Endpoints complementares a considerar (mesma paridade do de perfil)

O de perfil também tem `GET` (download/serve da imagem) e `DELETE`
(remoção). Não são estritamente necessários para o app Android hoje (ele só
faz upload no momento da criação), mas ficam registrados aqui para não
esquecer caso a paridade completa seja desejada depois:

- `GET /api/v1/vehicles/{id}/photo` — bytes da imagem (`200`) ou `204` se o
  veículo não tem foto.
- `DELETE /api/v1/vehicles/{id}/photo` — remove a foto (`204`).

**Fora de escopo por enquanto** (não pedido pelo client): esses dois acima.
Só o `POST` é bloqueante para o app funcionar.

## Onde populares o resultado

`VehicleResponseDTO.photo: String?` já existe no schema (hoje sempre
`null`, porque nada escreve nele). Este endpoint deve ser o único ponto de
escrita desse campo — `VehicleRequestDTO` (usado tanto para criar quanto
atualizar veículo) **não** deve ganhar um campo `photo`; o upload continua
sendo uma chamada separada, do mesmo jeito que já funciona para foto de
perfil.

## Checklist de implementação sugerido

- [ ] Controller: novo método em `VehicleController` (ou equivalente),
      espelhando a assinatura do método de upload de foto de perfil.
- [ ] Service: reusar o client de storage (S3/Backblaze B2) já usado pelo
      upload de perfil — mesma configuração de bucket/credenciais, só path
      diferente (ex.: `vehicles/{id}/photo.jpg` em vez de
      `users/{userId}/profile.jpg`).
- [ ] Validação de tipo/tamanho: reusar o validator já usado no upload de
      perfil, se for uma classe compartilhada; senão, duplicar a mesma
      lógica de whitelist (JPEG/PNG/WEBP) e limite (5MB).
- [ ] Autorização: usar o mesmo guard de "dono do veículo" já usado em
      `GET/PUT/DELETE /vehicles/{id}`.
- [ ] Persistir a URL retornada pelo storage no campo `photo` da entidade
      `Vehicle`.
- [ ] (Opcional, mas recomendado) Adicionar handler de `MultipartException`
      no `GlobalExceptionHandler` para devolver `400` consistente em vez de
      `500`/`413` quando o arquivo excede o limite do servlet — beneficia
      este endpoint e o de foto de perfil.
- [ ] Atualizar o `openapi.yaml` do backend com o novo path.
- [ ] Avisar o time do app (ou fechar a issue
      [flow-fuel-api#10](https://github.com/Rochafelip/flow-fuel-api/issues/10))
      quando estiver em produção — o client Android já está pronto e só
      aguardando o endpoint existir, nenhuma mudança adicional é necessária
      no app.
