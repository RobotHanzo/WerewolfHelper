import asyncio
import random
import time

import discord
from discord.ext import commands
from discord_slash import cog_ext, ComponentContext, SlashContext
from discord_slash.utils.manage_commands import create_option
from discord_slash.utils.manage_components import create_actionrow, create_button
from discord_slash.model import ButtonStyle


class Gameplay(commands.Cog):
    def __init__(self, bot):
        self.bot = bot
        self.slash = bot.slash
        self.allow_enroll = False
        self.original_candidates = []
        self.candidates = []

    @cog_ext.cog_slash(name='timer', options=[
        create_option(name='interval', description='秒數', option_type=int, required=True)
    ], description='計時器')
    @commands.has_permissions(administrator=True)
    async def timer_cmd(self, ctx: SlashContext, interval: int):
        cid = 'stop_timer_' + str(time.time())
        msg = await ctx.send(f'開始計時 {interval}秒\n結束時間：<t:{int(time.time() + interval)}:T>',
                             components=[create_actionrow(create_button(ButtonStyle.red, label='結束計時',
                                                                        custom_id=cid))])
        try:
            def check(comp_ctx: ComponentContext):
                return ctx.author.id == comp_ctx.author.id & comp_ctx.custom_id == cid

            await self.bot.wait_for('component', check=check, timeout=interval)
            await msg.reply('成功停止計時')
        except asyncio.TimeoutError:
            await msg.reply('時間到')

    @cog_ext.cog_slash(name='expel_poll', description='針對活著的玩家舉行放逐投票')
    @commands.has_permissions(administrator=True)
    async def expel_poll(self, ctx):
        alive = []
        sorted_member = sorted(ctx.guild.members, key=lambda x: x.display_name)
        for member in sorted_member:
            member: discord.Member
            if any([i.name.startswith('玩家') for i in member.roles]):
                alive.append((f'{member.display_name} ({str(member)})', str(member.id)))
        bot = self.bot

        async def voter_check(int_ctx):
            if any([i.name.startswith('玩家') for i in int_ctx.author.roles]):
                return True
            else:
                await int_ctx.send('只有活人才能投票！', hidden=True)
                return False

        async def callback(winner):
            parsed = []
            for w in winner:
                parsed.append(f'<@!{list(w.keys())[0]}>')
            await ctx.send(f'投票結束，被放逐者：{"、".join(parsed)}')
            return

        await self.bot.voting.generate_new_poll(ctx, '放逐投票', alive, 1, 20, False, False, callback, voter_check)

    @cog_ext.cog_slash(name='police_enroll', description='發動參選警長的投票')
    @commands.has_permissions(administrator=True)
    async def _police_enroll(self, ctx):
        self.allow_enroll = True
        embed = discord.Embed(title='欲參選警長者請按下按鈕', description='時間只有15秒！請加快手速！')
        msg = await ctx.send(embed=embed, components=[
            create_actionrow(create_button(style=ButtonStyle.green, label='參選警長', custom_id='police_enroll'))])
        self.original_candidates = []
        self.candidates = []

        @self.slash.component_callback(use_callback_name=False, messages=msg)
        async def police_enroll(btn_ctx: ComponentContext):
            if self.allow_enroll:
                if btn_ctx.author not in self.candidates:
                    self.original_candidates.append(btn_ctx.author)
                    self.candidates.append(btn_ctx.author)
                    await btn_ctx.send('參選成功！', hidden=True)
                else:
                    self.candidates.remove(btn_ctx.author)
                    self.original_candidates.remove(btn_ctx.author)
                    await btn_ctx.send('退選成功！', hidden=True)
            else:
                if btn_ctx.author in self.candidates:
                    self.candidates.remove(btn_ctx.author)
                    await btn_ctx.send('退選成功！', hidden=True)
                    await btn_ctx.send(btn_ctx.author.mention + '已退選！')

        await asyncio.sleep(10)
        await ctx.send('剩下5秒!')
        await asyncio.sleep(5)
        self.allow_enroll = False
        if len(self.candidates) == 0:
            await ctx.send('無人參選警長！')
        else:
            res = sorted(x.display_name for x in self.candidates)
            await ctx.send(f'參選的有: {"，".join(res)}')
            order = random.choice(['上', '下'])
            speaker = random.choice(res)
            await ctx.send(f'發言順序：{speaker}{order}\n\n注意：已參選者可以隨時再按一次來退選，但不可參選')

    @cog_ext.cog_slash(name='police_poll', description='上警投票')
    @commands.has_permissions(administrator=True)
    async def police_poll(self, ctx):
        if len(self.candidates) == 0:
            await ctx.send('無人參選警長或尚未啟動參選警長選單！')
            return
        candidates = []
        sorted_member = sorted(self.candidates, key=lambda x: x.display_name)
        for member in sorted_member:
            member: discord.Member
            candidates.append((f'{member.display_name} ({str(member)})', str(member.id)))
        bot = self.bot

        async def voter_check(int_ctx):
            if any([i.name.startswith('玩家') for i in
                    int_ctx.author.roles]) and int_ctx.author not in self.original_candidates:
                return True
            else:
                await int_ctx.send('只有活人和未曾參選者才能投票！', hidden=True)
                return False

        async def callback(winner):
            parsed = []
            for w in winner:
                parsed.append(f'<@!{list(w.keys())[0]}>')
            await ctx.send(f'投票結束，當選警長者：{"、".join(parsed)}')
            return

        await self.bot.voting.generate_new_poll(ctx, '警長投票', candidates, 1, 20, False, False, callback, voter_check)

    @cog_ext.cog_slash(name='order', description='隨機發言順序')
    async def order(self, ctx):
        alives = []
        sorted_member = sorted(ctx.guild.members, key=lambda x: x.display_name)
        for member in sorted_member:
            member: discord.Member
            if any([i.name.startswith('玩家') for i in member.roles]):
                alives.append(member)
        person = random.choice(alives)
        order = random.choice(['上', '下'])
        await ctx.send(f'順序：{person.display_name} {order}')

    @cog_ext.cog_slash(name='dead', options=[
        create_option(name='member', description='要使變成旁觀者/死人的人', option_type=6, required=True)
    ], description='讓人變成旁觀者或死人')
    @commands.has_permissions(administrator=True)
    async def dead(self, ctx, member: discord.Member):
        dead_role = discord.utils.get(ctx.guild.roles, name='旁觀者 / 死人')
        for i in member.roles:
            if i.name.startswith('玩家'):
                await member.remove_roles(i)
        await member.add_roles(dead_role)
        await ctx.send('完成')

    @cog_ext.cog_slash(name='daytime', description='進入白天')
    @commands.has_permissions(administrator=True)
    async def daytime(self, ctx):
        await ctx.defer()
        court = discord.utils.get(ctx.guild.stage_channels, name='法院')
        for vc in ctx.guild.voice_channels:
            vc: discord.VoiceChannel
            if vc.name == '法院':
                continue
            else:
                for m in vc.members:
                    await m.move_to(court)
        await ctx.channel.send('天亮了', tts=True)


def setup(bot):
    bot.add_cog(Gameplay(bot))
